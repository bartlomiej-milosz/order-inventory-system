"""HTTP walkthroughs. Each stock or order scenario creates its own catalog records."""

from secrets import token_hex
from urllib.parse import urlencode

from .client import DemoError, expect_fields, read_password


def health(api):
    for path in ("/livez", "/readyz"):
        expect_fields(api.request("GET", path), status="UP")


def employee_accounts(api, args):
    users = "/api/v1/users"
    if args.action == "create":
        password = read_password("New employee password")
        if password != read_password("Repeat new employee password"):
            raise DemoError("Employee passwords do not match")
        employee = api.request(
            "POST",
            users,
            {"email": args.email, "password": password, "role": args.role},
            status=201,
        )
        expect_fields(employee, role=args.role, active=True)
        print(f"Created {employee['role']} {employee['email']}, ID {employee['id']}")
    elif args.action == "role":
        employee = api.request("PUT", f"{users}/{args.id}/role", {"role": args.role})
        expect_fields(employee, role=args.role)
        print(f"Employee {args.id} now has role {args.role}")
    else:
        api.request("POST", f"{users}/{args.id}/deactivate", status=204)


class Catalog:
    def __init__(self, api):
        self.api = api
        self.code = "DEMO-" + token_hex(6).upper()
        self.customer = api.request(
            "POST",
            "/api/v1/customers",
            {"name": "Demo Retail", "email": "buyer@example.test"},
            status=201,
        )["id"]
        self.warehouse = api.request(
            "POST", "/api/v1/warehouses", {"code": self.code, "name": "Demo warehouse"}, status=201
        )["id"]
        self.product = self.add_product(self.code, "Original widget", 12.50)
        self.stock = f"/api/v1/warehouses/{self.warehouse}/stock/{self.product}"

    def add_product(self, sku, name, price):
        product = self.api.request(
            "POST", "/api/v1/products", {"sku": sku, "name": name, "unitPrice": price}, status=201
        )
        return product["id"]

    def receive_ten(self):
        self.api.request(
            "POST",
            self.stock + "/receipts",
            {"quantity": 10, "note": "Initial delivery"},
            status=201,
        )

    def check_stock(self, on_hand, reserved):
        result = self.api.request("GET", self.stock)
        available = on_hand - reserved
        expect_fields(result, onHand=on_hand, reserved=reserved, available=available)
        print(f"Stock: {on_hand} on hand, {reserved} reserved, {available} available")

    def draft(self, suffix):
        header = {
            "orderNumber": f"{self.code}-{suffix}",
            "customerId": self.customer,
            "warehouseId": self.warehouse,
        }
        detail = self.api.request("POST", "/api/v1/orders", header, status=201)
        expect_fields(detail["order"], status="DRAFT", total=0)
        expect_fields(detail, items=[])
        return "/api/v1/orders/" + detail["order"]["id"]

    def add_line(self, order, quantity):
        item = self.api.request(
            "POST", order + "/items", {"productId": self.product, "quantity": quantity}, status=201
        )
        return order + "/items/" + item["id"]


def catalog_stock(api):
    catalog = Catalog(api)
    catalog.check_stock(0, 0)
    catalog.receive_ten()
    adjustments = catalog.stock + "/adjustments"
    api.request("POST", adjustments, {"quantityDelta": -2, "note": "Damaged packaging"}, status=201)
    api.request("POST", adjustments, {"quantityDelta": -9, "note": "Exceeds stock"}, status=409)
    catalog.check_stock(8, 0)
    history = api.request("GET", catalog.stock + "/movements?size=2")
    expect_fields(history, totalElements=2)
    if sorted(item["quantityDelta"] for item in history["items"]) != [-2, 10]:
        raise DemoError("Expected the receipt of 10 and the correction of -2")


def draft_orders(api):
    catalog = Catalog(api)
    extra = catalog.add_product("EXTRA-" + catalog.code, "Extra pack", 2.00)
    before = api.request("GET", catalog.stock)
    order = catalog.draft("DRAFT")
    line = catalog.add_line(order, 3)
    api.request("POST", order + "/items", {"productId": catalog.product, "quantity": 1}, status=409)

    api.request(
        "PUT",
        "/api/v1/products/" + catalog.product,
        {"sku": "NEW-" + catalog.code, "name": "Updated widget", "unitPrice": 99.99},
    )
    edited = api.request("PUT", line, {"quantity": 4})
    expect_fields(
        edited,
        quantity=4,
        productSku=catalog.code,
        productName="Original widget",
        unitPrice=12.50,
        lineTotal=50,
    )

    extra_item = api.request(
        "POST", order + "/items", {"productId": extra, "quantity": 2}, status=201
    )
    detail = api.request(
        "PUT",
        order,
        {
            "customerId": catalog.customer,
            "warehouseId": catalog.warehouse,
            "note": "Draft checked with the customer",
        },
    )
    expect_fields(detail["order"], total=54)
    if len(detail["items"]) != 2:
        raise DemoError("Expected both draft lines")
    api.request("DELETE", order + "/items/" + extra_item["id"], status=204)
    detail = api.request("GET", order)
    expect_fields(detail["order"], status="DRAFT", total=50)
    expect_fields(detail, items=[edited])

    filters = urlencode(
        {
            "status": "DRAFT",
            "customerId": catalog.customer,
            "warehouseId": catalog.warehouse,
            "size": 1,
        }
    )
    page = api.request("GET", "/api/v1/orders?" + filters)
    expect_fields(page, totalElements=1)
    expect_fields(page["items"][0], id=detail["order"]["id"], total=50)
    expect_fields(api.request("GET", catalog.stock), **before)
    print("Draft total: 4 × 12.50 PLN = 50.00 PLN. Stock is unchanged.")


def confirm_orders(api):
    catalog = Catalog(api)
    catalog.receive_ten()
    first = catalog.draft("FIRST")
    catalog.add_line(first, 6)
    confirmed = api.request("POST", first + "/confirm")["order"]
    expect_fields(confirmed, status="CONFIRMED")
    if not confirmed["confirmedAt"]:
        raise DemoError("Missing confirmation time")
    catalog.check_stock(10, 6)

    second = catalog.draft("SECOND")
    line = catalog.add_line(second, 5)
    api.request("POST", second + "/confirm", status=409)
    expect_fields(api.request("GET", second)["order"], status="DRAFT", confirmedAt=None)
    catalog.check_stock(10, 6)
    api.request("PUT", line, {"quantity": 4})
    detail = api.request("POST", second + "/confirm")
    expect_fields(detail["order"], status="CONFIRMED")
    expect_fields(detail["items"][0], quantity=4)
    api.request("POST", first + "/confirm", status=409)
    catalog.check_stock(10, 10)
    history = api.request("GET", catalog.stock + "/movements")
    expect_fields(history, totalElements=1)
    expect_fields(history["items"][0], type="IN", quantityDelta=10)


def complete_orders(api):
    catalog = Catalog(api)
    catalog.receive_ten()
    sale = catalog.draft("SALE")
    catalog.add_line(sale, 3)
    api.request("POST", sale + "/confirm")
    catalog.check_stock(10, 3)
    fulfilled = api.request("POST", sale + "/fulfill")["order"]
    expect_fields(fulfilled, status="FULFILLED")
    if not fulfilled["confirmedAt"]:
        raise DemoError("The fulfilled order lost its confirmation time")
    catalog.check_stock(7, 0)
    api.request("POST", sale + "/cancel", status=409)

    reserved = catalog.draft("RELEASE")
    catalog.add_line(reserved, 2)
    confirmed = api.request("POST", reserved + "/confirm")["order"]
    if not confirmed["confirmedAt"]:
        raise DemoError("Missing confirmation time")
    catalog.check_stock(7, 2)
    cancelled = api.request("POST", reserved + "/cancel")["order"]
    expect_fields(cancelled, status="CANCELLED", confirmedAt=confirmed["confirmedAt"])
    catalog.check_stock(7, 0)

    draft = catalog.draft("CANCEL")
    catalog.add_line(draft, 20)
    expect_fields(
        api.request("POST", draft + "/cancel")["order"], status="CANCELLED", confirmedAt=None
    )
    catalog.check_stock(7, 0)
    history = api.request("GET", catalog.stock + "/movements")
    expect_fields(history, totalElements=2)
    movements = [(row["type"], row["quantityDelta"], row["orderId"]) for row in history["items"]]
    if sorted(movements) != [("IN", 10, None), ("OUT", -3, fulfilled["id"])]:
        raise DemoError(f"Unexpected movement history: {movements}")
    print(f"Movements: IN +10 and OUT -3 for fulfilled order {fulfilled['id']}")
