from functools import lru_cache

from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    model_config = SettingsConfigDict(env_file=".env", env_file_encoding="utf-8", extra="ignore")

    app_name: str = "motion-server"
    environment: str = "local"  # local | dev | prod
    host: str = "0.0.0.0"
    port: int = 8000

    # MongoDB 설정 (Spring과 동일한 MongoDB 사용)
    mongodb_uri: str = "mongodb://heungbu:lastdance@heungbuja-mongo:27017/heungbudb?authSource=admin"


@lru_cache
def get_settings() -> Settings:
    return Settings()


