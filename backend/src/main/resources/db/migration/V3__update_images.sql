-- Update images with placeholder URLs

-- Update category images
UPDATE categories SET image_url = 'https://images.unsplash.com/photo-1498049794561-7780e7231661?w=400' WHERE slug = 'electronics';
UPDATE categories SET image_url = 'https://images.unsplash.com/photo-1445205170230-053b83016050?w=400' WHERE slug = 'clothing';
UPDATE categories SET image_url = 'https://images.unsplash.com/photo-1484101403633-562f891dc89a?w=400' WHERE slug = 'home-garden';
UPDATE categories SET image_url = 'https://images.unsplash.com/photo-1461896836934- voices-of-the-world?w=400' WHERE slug = 'sports';

-- Update product images
UPDATE products SET image_url = 'https://images.unsplash.com/photo-1695048133142-1a20484d2569?w=400' WHERE sku = 'IPHONE15PRO-256';
UPDATE products SET image_url = 'https://images.unsplash.com/photo-1517336714731-489689fd1ca8?w=400' WHERE sku = 'MBP16-M3MAX-32-1TB';
UPDATE products SET image_url = 'https://images.unsplash.com/photo-1521572163474-6864f9cf17ab?w=400' WHERE sku = 'TSHIRT-COTTON-001';
UPDATE products SET image_url = 'https://images.unsplash.com/photo-1542291026-7eec264c27ff?w=400' WHERE sku = 'SHOES-RUN-PRO-001';
