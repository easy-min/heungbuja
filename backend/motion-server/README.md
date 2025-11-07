motion-server (FastAPI)

Run locally:
- python -m venv .venv
- . .venv/Scripts/activate
- pip install -r requirements.txt
- uvicorn app.main:app --reload --host 0.0.0.0 --port 8000

Endpoints:
- GET /health
- GET /


