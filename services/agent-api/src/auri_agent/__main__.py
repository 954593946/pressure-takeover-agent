import uvicorn

from .config import Settings


def main() -> None:
    settings = Settings()
    uvicorn.run("auri_agent.app:app", host=settings.host, port=settings.port, reload=False)


if __name__ == "__main__":
    main()
