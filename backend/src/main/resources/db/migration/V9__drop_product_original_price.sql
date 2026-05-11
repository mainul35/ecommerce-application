-- products.original_price was a manual MSRP / "compare-at" anchor from before
-- the discount-campaign system existed. With campaign discounts (Discount entity)
-- now providing the same display semantics (strikethrough sticker price plus a
-- sale price), the manual anchor is redundant and confusing. Drop it.
--
-- Existing installs lose any per-row anchor values; campaigns on /admin/discounts
-- (PRODUCT / CATEGORY / SITEWIDE) cover all the use-cases that originalPrice did.

ALTER TABLE products
    DROP COLUMN IF EXISTS original_price;
