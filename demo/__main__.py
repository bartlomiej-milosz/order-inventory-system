"""Run with python3 -m demo from the repository root."""

import argparse
import os
import subprocess
import sys
from uuid import UUID

from . import scenarios, sql_runner
from .client import Api, DemoError

WALKTHROUGHS = {
    "catalog-stock": (scenarios.catalog_stock, "receive and correct stock"),
    "draft-orders": (scenarios.draft_orders, "edit a draft and verify its saved prices"),
    "confirm-orders": (scenarios.confirm_orders, "reserve stock for competing orders"),
    "complete-orders": (scenarios.complete_orders, "fulfill and cancel orders"),
}


def parse_args():
    parser = argparse.ArgumentParser(
        prog="python3 -m demo", description="Explore the Order & Inventory System"
    )
    parser.add_argument("--verbose", action="store_true", help="also print HTTP response bodies")
    commands = parser.add_subparsers(dest="command", required=True)
    commands.add_parser("health", help="check liveness and readiness")
    access = commands.add_parser("employee-access", help="compare anonymous and signed-in access")
    access.add_argument("employee_email")

    for name, (_, description) in WALKTHROUGHS.items():
        command = commands.add_parser(name, help=description)
        command.add_argument("admin_email")

    accounts = commands.add_parser(
        "employee-accounts", help="create, promote or deactivate an employee"
    )
    accounts.add_argument("admin_email")
    actions = accounts.add_subparsers(dest="action", required=True)
    create = actions.add_parser("create")
    create.add_argument("email")
    create.add_argument("role", choices=("ADMIN", "OPERATOR"))
    role = actions.add_parser("role")
    role.add_argument("id", type=UUID)
    role.add_argument("role", choices=("ADMIN", "OPERATOR"))
    actions.add_parser("deactivate").add_argument("id", type=UUID)

    sql = commands.add_parser("sql", help="prepare the separate demo database or run a report")
    sql.add_argument("action", choices=("setup", "orders", "stock", "consistency"))
    return parser.parse_args()


def main():
    args = parse_args()
    if args.command == "sql":
        sql_runner.report(args.action)
        return
    api = Api(os.environ.get("BASE_URL", "http://127.0.0.1:8080"), verbose=args.verbose)
    if args.command == "health":
        scenarios.health(api)
    elif args.command == "employee-access":
        api.request("GET", "/api/v1/auth/me", status=401)
        api.sign_in(args.employee_email)
    else:
        api.sign_in(args.admin_email, admin=True)
        if args.command == "employee-accounts":
            scenarios.employee_accounts(api, args)
        else:
            run_scenario, _ = WALKTHROUGHS[args.command]
            run_scenario(api)


if __name__ == "__main__":
    try:
        main()
    except KeyboardInterrupt:
        sys.exit(130)
    except (DemoError, OSError, ValueError, subprocess.CalledProcessError) as error:
        sys.exit(f"Demo failed: {error}")
