CREATE TABLE inventory.users (
    id uuid PRIMARY KEY,
    email varchar(254) NOT NULL UNIQUE,
    password_hash text NOT NULL,
    role varchar(20) NOT NULL,
    active boolean NOT NULL DEFAULT true,
    created_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT users_normalized_email CHECK (
        email <> ''
        AND email = lower(email)
        AND email !~ '[[:space:]]'
    ),
    CONSTRAINT users_valid_role CHECK (role IN ('ADMIN', 'OPERATOR'))
);
CREATE TABLE inventory.customers (
    id uuid PRIMARY KEY,
    name varchar(200) NOT NULL,
    email varchar(254) NOT NULL,
    phone varchar(30),
    created_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT customers_nonblank_name CHECK (name ~ '[^[:space:]]')
);
CREATE TABLE inventory.products (
    id uuid PRIMARY KEY,
    sku varchar(50) NOT NULL UNIQUE,
    name varchar(200) NOT NULL,
    unit_price numeric(12, 2) NOT NULL,
    active boolean NOT NULL DEFAULT true,
    created_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT products_nonblank_sku CHECK (sku ~ '[^[:space:]]'),
    CONSTRAINT products_nonblank_name CHECK (name ~ '[^[:space:]]'),
    CONSTRAINT products_valid_price CHECK (
        unit_price >= 0
        AND unit_price <> 'NaN'::numeric
    )
);
CREATE TABLE inventory.warehouses (
    id uuid PRIMARY KEY,
    code varchar(30) NOT NULL UNIQUE,
    name varchar(100) NOT NULL,
    active boolean NOT NULL DEFAULT true,
    CONSTRAINT warehouses_nonblank_code CHECK (code ~ '[^[:space:]]'),
    CONSTRAINT warehouses_nonblank_name CHECK (name ~ '[^[:space:]]')
);
CREATE TABLE inventory.stock_levels (
    id uuid PRIMARY KEY,
    product_id uuid NOT NULL REFERENCES inventory.products (id) ON DELETE RESTRICT,
    warehouse_id uuid NOT NULL REFERENCES inventory.warehouses (id) ON DELETE RESTRICT,
    on_hand integer NOT NULL DEFAULT 0,
    reserved integer NOT NULL DEFAULT 0,
    updated_at timestamptz NOT NULL,
    CONSTRAINT stock_levels_product_warehouse_key UNIQUE (product_id, warehouse_id),
    CONSTRAINT stock_levels_valid_quantities CHECK (
        on_hand >= 0
        AND reserved >= 0
        AND reserved <= on_hand
    )
);
CREATE TABLE inventory.sales_orders (
    id uuid PRIMARY KEY,
    order_number varchar(30) NOT NULL UNIQUE,
    customer_id uuid NOT NULL REFERENCES inventory.customers (id) ON DELETE RESTRICT,
    warehouse_id uuid NOT NULL REFERENCES inventory.warehouses (id) ON DELETE RESTRICT,
    created_by uuid NOT NULL REFERENCES inventory.users (id) ON DELETE RESTRICT,
    status varchar(20) NOT NULL DEFAULT 'DRAFT',
    created_at timestamptz NOT NULL DEFAULT now(),
    confirmed_at timestamptz,
    note text,
    CONSTRAINT sales_orders_nonblank_number CHECK (order_number ~ '[^[:space:]]'),
    CONSTRAINT sales_orders_valid_status CHECK (
        status IN ('DRAFT', 'CONFIRMED', 'FULFILLED', 'CANCELLED')
    ),
    CONSTRAINT sales_orders_valid_confirmation CHECK (
        (
            status = 'DRAFT'
            AND confirmed_at IS NULL
        )
        OR (
            status IN ('CONFIRMED', 'FULFILLED')
            AND confirmed_at IS NOT NULL
        )
        OR status = 'CANCELLED'
    )
);
CREATE TABLE inventory.order_items (
    id uuid PRIMARY KEY,
    order_id uuid NOT NULL REFERENCES inventory.sales_orders (id) ON DELETE RESTRICT,
    product_id uuid NOT NULL REFERENCES inventory.products (id) ON DELETE RESTRICT,
    product_name varchar(200) NOT NULL,
    product_sku varchar(50) NOT NULL,
    quantity integer NOT NULL,
    unit_price numeric(12, 2) NOT NULL,
    CONSTRAINT order_items_order_product_key UNIQUE (order_id, product_id),
    CONSTRAINT order_items_nonblank_product_name CHECK (product_name ~ '[^[:space:]]'),
    CONSTRAINT order_items_nonblank_product_sku CHECK (product_sku ~ '[^[:space:]]'),
    CONSTRAINT order_items_positive_quantity CHECK (quantity > 0),
    CONSTRAINT order_items_valid_price CHECK (
        unit_price >= 0
        AND unit_price <> 'NaN'::numeric
    )
);
CREATE TABLE inventory.stock_movements (
    id uuid PRIMARY KEY,
    stock_level_id uuid NOT NULL REFERENCES inventory.stock_levels (id) ON DELETE RESTRICT,
    performed_by uuid NOT NULL REFERENCES inventory.users (id) ON DELETE RESTRICT,
    order_id uuid REFERENCES inventory.sales_orders (id) ON DELETE RESTRICT,
    type varchar(20) NOT NULL,
    quantity_delta integer NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    note text,
    CONSTRAINT stock_movements_valid_shape CHECK (
        (
            type = 'IN'
            AND quantity_delta > 0
            AND order_id IS NULL
        )
        OR (
            type = 'OUT'
            AND quantity_delta < 0
            AND order_id IS NOT NULL
        )
        OR (
            type = 'ADJUSTMENT'
            AND quantity_delta <> 0
            AND order_id IS NULL
            AND note IS NOT NULL
            AND note ~ '[^[:space:]]'
        )
    )
);
CREATE INDEX stock_levels_warehouse_idx ON inventory.stock_levels (warehouse_id);
CREATE INDEX sales_orders_customer_idx ON inventory.sales_orders (customer_id);
CREATE INDEX sales_orders_warehouse_idx ON inventory.sales_orders (warehouse_id);
CREATE INDEX sales_orders_creator_idx ON inventory.sales_orders (created_by);
CREATE INDEX order_items_product_idx ON inventory.order_items (product_id);
CREATE INDEX stock_movements_stock_level_idx ON inventory.stock_movements (stock_level_id);
CREATE INDEX stock_movements_performer_idx ON inventory.stock_movements (performed_by);
CREATE INDEX stock_movements_order_idx ON inventory.stock_movements (order_id);