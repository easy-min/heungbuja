import logging
from logging.config import dictConfig

from fastapi import FastAPI

from app.api.routes.analyze import router as analyze_router
from app.api.routes.health import router as health_router



def create_app() -> FastAPI:
    dictConfig(
        {
            "version": 1,
            "disable_existing_loggers": False,
            "formatters": {
                "default": {
                    "format": "%(asctime)s [%(levelname)s] %(name)s: %(message)s",
                },
            },
            "handlers": {
                "default": {
                    "class": "logging.StreamHandler",
                    "formatter": "default",
                    "level": "INFO",
                },
            },
            "root": {
                "handlers": ["default"],
                "level": "INFO",
            },
        }
    )

    app = FastAPI(title="motion-server", version="0.1.0")

    # Routers
    app.include_router(health_router)
    app.include_router(analyze_router)

    @app.get("/")
    async def root():
        return {"service": "motion-server", "status": "ok"}

    return app


app = create_app()


