-- Seed data for E-Commerce Platform

-- Insert admin user (password: admin123)
INSERT INTO users (id, email, password, first_name, last_name, role, is_active, email_verified)
VALUES (
    'a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11',
    'admin@ecommerce.com',
    '$2a$10$rqnSVNIFfQ0OPbZzP7t5beZlmNFzF.d1lOKITDaM9t3U0sG8WeCmS',
    'Admin',
    'User',
    'ADMIN',
    TRUE,
    TRUE
);

-- Insert sample categories
INSERT INTO categories (id, name, slug, description, image_url)
VALUES
    ('b0eebc99-9c0b-4ef8-bb6d-6bb9bd380a01', 'Electronics', 'electronics', 'Electronic devices and accessories', '/categories/electronics.jpg'),
    ('b0eebc99-9c0b-4ef8-bb6d-6bb9bd380a02', 'Clothing', 'clothing', 'Fashion and apparel', '/categories/clothing.jpg'),
    ('b0eebc99-9c0b-4ef8-bb6d-6bb9bd380a03', 'Home & Garden', 'home-garden', 'Home decor and garden supplies', '/categories/home-garden.jpg'),
    ('b0eebc99-9c0b-4ef8-bb6d-6bb9bd380a04', 'Sports', 'sports', 'Sports equipment and accessories', '/categories/sports.jpg');

-- Insert subcategories
INSERT INTO categories (id, name, slug, description, parent_id)
VALUES
    ('c0eebc99-9c0b-4ef8-bb6d-6bb9bd380a01', 'Smartphones', 'smartphones', 'Mobile phones and tablets', 'b0eebc99-9c0b-4ef8-bb6d-6bb9bd380a01'),
    ('c0eebc99-9c0b-4ef8-bb6d-6bb9bd380a02', 'Laptops', 'laptops', 'Laptops and notebooks', 'b0eebc99-9c0b-4ef8-bb6d-6bb9bd380a01'),
    ('c0eebc99-9c0b-4ef8-bb6d-6bb9bd380a03', 'Men', 'men', 'Men clothing', 'b0eebc99-9c0b-4ef8-bb6d-6bb9bd380a02'),
    ('c0eebc99-9c0b-4ef8-bb6d-6bb9bd380a04', 'Women', 'women', 'Women clothing', 'b0eebc99-9c0b-4ef8-bb6d-6bb9bd380a02');

-- Insert sample products
INSERT INTO products (id, name, description, price, original_price, image_url, category_id, attributes, stock, sku)
VALUES
    (
        'd0eebc99-9c0b-4ef8-bb6d-6bb9bd380a01',
        'iPhone 15 Pro',
        'Latest Apple iPhone with A17 Pro chip',
        999.99,
        1099.99,
        '/products/iphone15pro.jpg',
        'c0eebc99-9c0b-4ef8-bb6d-6bb9bd380a01',
        '{"color": "Space Black", "storage": "256GB", "display": "6.1 inch"}',
        50,
        'IPHONE15PRO-256'
    ),
    (
        'd0eebc99-9c0b-4ef8-bb6d-6bb9bd380a02',
        'MacBook Pro 16"',
        'Powerful laptop for professionals with M3 Max chip',
        2499.99,
        NULL,
        '/products/macbookpro16.jpg',
        'c0eebc99-9c0b-4ef8-bb6d-6bb9bd380a02',
        '{"processor": "M3 Max", "memory": "32GB", "storage": "1TB SSD"}',
        25,
        'MBP16-M3MAX-32-1TB'
    ),
    (
        'd0eebc99-9c0b-4ef8-bb6d-6bb9bd380a03',
        'Classic Cotton T-Shirt',
        'Comfortable 100% cotton t-shirt',
        29.99,
        39.99,
        '/products/cotton-tshirt.jpg',
        'c0eebc99-9c0b-4ef8-bb6d-6bb9bd380a03',
        '{"material": "100% Cotton", "sizes": ["S", "M", "L", "XL"], "colors": ["White", "Black", "Navy"]}',
        200,
        'TSHIRT-COTTON-001'
    ),
    (
        'd0eebc99-9c0b-4ef8-bb6d-6bb9bd380a04',
        'Running Shoes Pro',
        'Professional running shoes with advanced cushioning',
        149.99,
        179.99,
        '/products/running-shoes.jpg',
        'b0eebc99-9c0b-4ef8-bb6d-6bb9bd380a04',
        '{"sizes": [7, 8, 9, 10, 11, 12], "colors": ["Black/Red", "White/Blue"]}',
        75,
        'SHOES-RUN-PRO-001'
    );
