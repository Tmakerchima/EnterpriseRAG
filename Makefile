.PHONY: backend-test eval-test eval-smoke frontend-build compose-config

backend-test:
	cd backend && mvn -B clean test

eval-test:
	cd evaluation && python -m pip install -e ".[dev]" && python -m pytest

eval-smoke:
	cd evaluation && python -m enterprise_rag_eval smoke

frontend-build:
	cd frontend && npm ci && npm run build

compose-config:
	docker compose config
