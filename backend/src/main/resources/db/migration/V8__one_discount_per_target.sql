-- Enforce: at most ONE discount per product, ONE per category.
--
-- A product / category can have only one active or scheduled discount at a time;
-- to switch promotions the admin must edit or delete the existing one. Sitewide
-- discounts are unaffected (their scope_target_id is NULL, and Postgres treats
-- distinct NULLs as non-conflicting in a UNIQUE constraint - so multiple sitewide
-- campaigns can coexist).

-- Step 1: dedupe any existing duplicates from earlier iterations. For each
-- (scope, scope_target_id) keep the most recently created row, drop the rest.
DELETE FROM discounts d1
WHERE d1.scope IN ('PRODUCT', 'CATEGORY')
  AND EXISTS (
      SELECT 1 FROM discounts d2
      WHERE d2.scope = d1.scope
        AND d2.scope_target_id = d1.scope_target_id
        AND (d2.created_at > d1.created_at
             OR (d2.created_at = d1.created_at AND d2.id > d1.id))
  );

-- Step 2: enforce uniqueness going forward.
ALTER TABLE discounts
    ADD CONSTRAINT uq_discount_scope_target UNIQUE (scope, scope_target_id);
