import torch
import numpy as np
from speechbrain.inference.speaker import EncoderClassifier
from flask import Flask, request, jsonify
import io
import soundfile as sf
import onnxruntime as ort
import os
import json
import base64
import requests
from scipy.signal import wiener

np.seterr(divide='ignore', invalid='ignore')

app = Flask(__name__)

# Backend API Configuration
BACKEND_URL = os.getenv("BACKEND_URL", "http://localhost:8081")

MODEL_PREFIX = "SB_ECAPA_V1:"

# Check for GPU (RTX 4050)
device = "cuda" if torch.cuda.is_available() else "cpu"
print(f"Using device: {device}")

# 1. Load SpeechBrain ECAPA-TDNN (State-of-the-Art Speaker Verification Model)
print("Loading SpeechBrain ECAPA-TDNN Speaker Verification Model (speechbrain/spkrec-ecapa-voxceleb)...")
try:
    classifier = EncoderClassifier.from_hparams(
        source="speechbrain/spkrec-ecapa-voxceleb",
        run_opts={"device": device}
    )
    print("SpeechBrain ECAPA-TDNN loaded successfully on device:", device)
except Exception as e:
    print(f"Error loading SpeechBrain on {device}, falling back to CPU: {e}")
    classifier = EncoderClassifier.from_hparams(source="speechbrain/spkrec-ecapa-voxceleb")

# 2. Load Dhwani (for Indic Deepfake Detection)
print("Loading Dhwani Multilingual ONNX model...")
dhwani_path = "best_model.onnx"
if os.path.exists(dhwani_path):
    try:
        dhwani_session = ort.InferenceSession(dhwani_path, providers=['CUDAExecutionProvider', 'CPUExecutionProvider'])
        print("Dhwani loaded on GPU!")
    except Exception as e:
        dhwani_session = ort.InferenceSession(dhwani_path, providers=['CPUExecutionProvider'])
        print("Dhwani fallback to CPU successful.")
else:
    dhwani_session = None
    print(f"Warning: {dhwani_path} not found. Indic detection will be disabled.")

def extract_speaker_embedding(audio_data, sample_rate=16000):
    try:
        tensor = torch.tensor(audio_data, dtype=torch.float32).unsqueeze(0).to(device)
        with torch.no_grad():
            emb = classifier.encode_batch(tensor)
            norm_emb = torch.nn.functional.normalize(emb[0, 0], p=2, dim=-1)
            return norm_emb.cpu().numpy()
    except Exception as e:
        print(f"SpeechBrain Embedding Extraction Error: {e}")
        return np.zeros(192, dtype=np.float32)

def fetch_vault_from_backend(owner_id):
    try:
        response = requests.get(f"{BACKEND_URL}/api/v1/vault/list", params={"ownerId": owner_id}, timeout=5)
        if response.status_code == 200:
            return response.json()
        return []
    except Exception as e:
        print(f"Backend Fetch Error: {e}")
        return []

def save_to_backend_vault(owner_id, contact_id, name, embedding, wps=0.0, pitch=0.0):
    try:
        emb_str = MODEL_PREFIX + ",".join(map(str, embedding))
        payload = {
            "ownerId": owner_id,
            "contactId": contact_id,
            "name": name,
            "embedding": emb_str,
            "wps": float(wps),
            "pitch": float(pitch)
        }
        response = requests.post(f"{BACKEND_URL}/api/v1/vault/save", json=payload, timeout=5)
        return response.status_code == 200
    except Exception as e:
        print(f"Backend Save Error: {e}")
        return False

def get_identity_score(vault, target_id, current_embedding, current_wps, current_pitch):
    if not target_id or target_id == "null" or target_id == "undefined" or target_id == "":
        return 1.0, 1.0

    # Find target profile in vault
    target_profile = next((item for item in vault if item["id"] == target_id), None)

    if not target_profile:
        print(f"Cross-Check Notice: Target ID '{target_id}' not found in vault.")
        return 1.0, 1.0

    stored_embedding_str = target_profile.get("embedding", "")
    if not stored_embedding_str:
        print(f"Cross-Check Notice: Target '{target_id}' has empty embedding.")
        return 1.0, 1.0

    # Verify model compatibility marker
    if stored_embedding_str.startswith(MODEL_PREFIX):
        raw_vec_str = stored_embedding_str[len(MODEL_PREFIX):]
    else:
        print(f"Notice: Legacy model embedding in DB for '{target_id}'. Please tap 'Enroll DNA' to update this voice.")
        return 1.0, 1.0

    try:
        stored_embedding = np.array(list(map(float, raw_vec_str.split(","))), dtype=np.float32)
    except Exception as e:
        print(f"Error parsing stored embedding for '{target_id}': {e}")
        return 1.0, 1.0

    norm_stored = np.linalg.norm(stored_embedding)
    norm_current = np.linalg.norm(current_embedding)

    if norm_stored == 0 or norm_current == 0:
        return 1.0, 1.0

    # Cosine Similarity between SpeechBrain ECAPA-TDNN L2-normalized embeddings
    raw_id_sim = float(np.dot(current_embedding, stored_embedding) / (norm_stored * norm_current))

    # Strict Speaker Verification Distance Mapping (Calibrated for SpeechBrain ECAPA-TDNN)
    # Same speaker >= 0.55 -> id_sim = 1.0 (Match)
    # Different speaker <= 0.35 -> id_sim = 0.0 (Mismatch / Voice Doesn't Match!)
    if raw_id_sim >= 0.55:
        id_sim = 1.0
    elif raw_id_sim <= 0.35:
        id_sim = 0.0
    else:
        id_sim = (raw_id_sim - 0.35) / 0.20

    id_sim = max(0.0, min(1.0, id_sim))

    # Behavioral similarity
    stored_wps = target_profile.get("wps", 0.0)
    stored_pitch = target_profile.get("pitch", 0.0)

    wps_diff = abs(current_wps - stored_wps) / (stored_wps + 1e-6)
    pitch_diff = abs(current_pitch - stored_pitch) / (stored_pitch + 1e-6)

    beh_sim = 1.0 - (min(wps_diff, 1.0) * 0.5 + min(pitch_diff, 1.0) * 0.5)

    print(f"Cross-Check -> Target: {target_profile.get('name', target_id)} | Cosine Sim: {raw_id_sim:.3f} | Identity Score: {id_sim:.2f}")

    return float(id_sim), float(beh_sim)

@app.route('/get_vault', methods=['GET'])
def get_vault():
    try:
        owner_id = request.args.get("ownerId", "unknown")
        vault = fetch_vault_from_backend(owner_id)
        # Reformat for app compatibility
        app_vault = [{
            "id": item["id"],
            "name": item["name"],
            "embedding": item.get("embedding", ""),
            "wps": item.get("wps", 0.0),
            "pitch": item.get("pitch", 0.0)
        } for item in vault]
        return jsonify({"vault": app_vault})
    except Exception as e:
        return jsonify({"error": str(e)}), 500

@app.route('/enroll_voice', methods=['POST'])
def enroll_voice():
    try:
        data = request.json
        owner_id = data.get("ownerId")
        contact_id = data.get("contactId")
        name = data.get("name", "Known Contact")
        samples_b64 = data.get("samples", [])

        if not owner_id or not contact_id or not samples_b64:
            return jsonify({"success": False, "error": "Missing ownerId, contactId, or samples"}), 400

        embeddings = []
        for b64_str in samples_b64:
            try:
                raw_bytes = base64.b64decode(b64_str)
                audio_data, sr = sf.read(io.BytesIO(raw_bytes))
                audio_data = audio_data.astype(np.float32)

                # VAD Filter
                if np.sqrt(np.mean(audio_data**2)) >= 1e-3:
                    emb = extract_speaker_embedding(audio_data, sr)
                    embeddings.append(emb)
            except Exception as e:
                print(f"Sample parsing error: {e}")

        if not embeddings:
            return jsonify({"success": False, "error": "No valid speech detected in enrollment samples"}), 400

        # Compute L2-normalized Centroid Vector across all enrollment samples
        centroid = np.mean(embeddings, axis=0)
        norm_centroid = centroid / (np.linalg.norm(centroid) + 1e-8)

        success = save_to_backend_vault(
            owner_id=owner_id,
            contact_id=contact_id,
            name=name,
            embedding=norm_centroid,
            wps=1.5,
            pitch=150.0
        )

        return jsonify({
            "success": success,
            "samplesProcessed": len(embeddings),
            "message": f"Successfully enrolled voice fingerprint for {name} ({len(embeddings)} samples)!"
        })
    except Exception as e:
        print(f"Enrollment Exception: {e}")
        return jsonify({"success": False, "error": str(e)}), 500

@app.route('/analyze_voice', methods=['POST'])
def analyze_voice():
    try:
        audio_file = request.data
        user_id = request.args.get("userId", "unknown")
        owner_id = request.args.get("ownerId", "unknown")
        user_name = request.args.get("name", "Unknown Caller")
        check_against = request.args.get("checkAgainst")
        live_wps = float(request.args.get("wps", 0.0))
        live_pitch = float(request.args.get("pitch", 0.0))

        target_id = check_against if (check_against and check_against != "null" and check_against != "undefined") else None

        data, samplerate = sf.read(io.BytesIO(audio_file))
        data = data.astype(np.float32)

        # 1. Pre-filter Silence/Energy Check
        energy = np.sqrt(np.mean(data**2))
        if energy < 1e-3:
            return jsonify({
                "dhwaniRisk": 0.0,
                "identityScore": 1.0,
                "behavioralScore": 1.0,
                "isSpeech": False
            })

        # 2. Audio Denoising
        try:
            if np.var(data) > 1e-7:
                data = wiener(data)
        except Exception as e:
            print(f"Wiener Error: {e}")

        # --- A. Speaker Embedding Extraction ---
        current_embedding = extract_speaker_embedding(data, samplerate)

        # Fetch latest vault from backend for analysis
        vault = fetch_vault_from_backend(owner_id)
        identity_score, behavioral_score = get_identity_score(vault, target_id, current_embedding, live_wps, live_pitch)

        # --- B. Single-Chunk Enrollment Mode (Optional) ---
        if request.args.get("enroll") == "true":
            if save_to_backend_vault(owner_id, user_id, user_name, current_embedding, live_wps, live_pitch):
                print(f"ENROLLMENT: {user_name} saved to Backend Vault for {owner_id}")

        # --- C. Dhwani Deepfake Analysis ---
        dhwani_risk = 0.0
        if dhwani_session:
            input_data = data.astype(np.float32)
            if input_data.ndim == 1:
                input_data = np.expand_dims(input_data, axis=0)

            inputs_onnx = {dhwani_session.get_inputs()[0].name: input_data}
            outputs = dhwani_session.run(None, inputs_onnx)

            raw_score = outputs[0][0]
            if isinstance(raw_score, np.ndarray):
                s_score = 1 / (1 + np.exp(-raw_score[1]))
                dhwani_risk = float(s_score) * 100.0
            else:
                s_score = 1 / (1 + np.exp(-raw_score))
                dhwani_risk = float(s_score) * 100.0

            dhwani_risk = max(0.0, min(100.0, dhwani_risk))

        print(f"Analysis -> User: {user_name} | Target: {target_id} | Identity Match: {identity_score:.2f} | Behav: {behavioral_score:.2f} | Dhwani: {dhwani_risk:.2f}%")

        return jsonify({
            "dhwaniRisk": dhwani_risk,
            "identityScore": identity_score,
            "behavioralScore": behavioral_score,
            "isSpeech": True
        })

    except Exception as e:
        print(f"Error: {e}")
        return jsonify({"error": str(e)}), 500

if __name__ == '__main__':
    app.run(host='0.0.0.0', port=5000)
