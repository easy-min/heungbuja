from fastapi import FastAPI

from app.api.routes.health import router as health_router
from app.api.routes.analyze import router as analyze_router


def create_app() -> FastAPI:
    app = FastAPI(title="music-server", version="0.1.0")

    # Routers
    app.include_router(health_router)
    app.include_router(analyze_router)

    @app.get("/")
    async def root():
        return {"service": "music-server", "status": "ok"}

    return app


app = create_app()
