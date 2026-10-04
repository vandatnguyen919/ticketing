# Retro findings

## 2026-10-03: decision text drifted when the frontend transport changed

Evidence: PRODUCT.md line 38 still describes caching and refreshing one masked CSRF
token per sign-in session. The app moved to Axios copying the raw XSRF-TOKEN cookie
into X-XSRF-TOKEN (commit 30dba6d), and the spec forbids caching and retrying. The raw
versus masked token question came back in both sessions on 2026-10-03 (7 and 17 turns).

Watch for: the next auth session re-asking which token shape the client sends, or
trusting the old decision wording. PRODUCT.md line 38 was corrected on 2026-10-04 to
state the raw cookie to header contract. Mark resolved once an auth session runs
without the question coming back.
