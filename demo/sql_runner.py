"""Run the project's PostgreSQL examples using local Compose and the Maven wrapper."""

import json
import os
import subprocess
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SQL = ROOT / "demo" / "sql"
DATABASE = "order_inventory_demo"
USER = "inventory_local"


def run(*args, **kwargs):
    return subprocess.run(args, cwd=ROOT, check=True, **kwargs)


def execute_file(filename, *, read_only):
    args = [
        "docker",
        "compose",
        "exec",
        "-T",
        "db",
        "psql",
        "-X",
        "-U",
        USER,
        "-d",
        DATABASE,
        "-v",
        "ON_ERROR_STOP=1",
        "-P",
        "pager=off",
        "--single-transaction",
    ]
    if read_only:
        args.extend(["-c", "SET TRANSACTION READ ONLY"])
    with (SQL / filename).open("rb") as source:
        run(*args, "-f", "-", stdin=source)


def setup():
    run("make", "init-env")
    run("docker", "compose", "up", "--wait", "db")
    # Compose resolves .env, overrides and port mappings using the same rules as the stack.
    config = run("docker", "compose", "config", "--format", "json", stdout=subprocess.PIPE)
    db = json.loads(config.stdout)["services"]["db"]
    port = next(p["published"] for p in db["ports"] if p["target"] == 5432)
    migration_env = {**os.environ, "FLYWAY_PASSWORD": db["environment"]["POSTGRES_PASSWORD"]}
    db_command = ("docker", "compose", "exec", "-T", "db")
    # A second setup must fail before cleanup can touch an existing database.
    run(*db_command, "createdb", "-U", USER, DATABASE)
    try:
        run(
            "./mvnw",
            "-B",
            "-ntp",
            "flyway:migrate",
            f"-Dflyway.url=jdbc:postgresql://127.0.0.1:{port}/{DATABASE}",
            f"-Dflyway.user={USER}",
            "-Dflyway.createSchemas=true",
            env=migration_env,
        )
        execute_file("seed.sql", read_only=False)
    except (Exception, KeyboardInterrupt):
        run(*db_command, "dropdb", "-U", USER, "--if-exists", DATABASE)
        raise
    print(f"Demo database {DATABASE} is ready.")


def report(action):
    if action == "setup":
        setup()
    else:
        execute_file(f"{action}.sql", read_only=True)
