from fastapi import FastAPI

from app.api.routes.health import router as health_router
from app.api.routes.inference import router as inference_router


def create_app() -> FastAPI:
    app = FastAPI(title="motion-server", version="0.1.0")

    # Routers
    app.include_router(health_router)
    app.include_router(inference_router)

    @app.get("/")
    async def root():
        return {"service": "motion-server", "status": "ok"}

    return app


app = create_app()


