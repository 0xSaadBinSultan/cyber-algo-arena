# Search visibility

The canonical public site is https://cyber-algo-arena.vercel.app/.

The homepage explains the combined competitive programming and capture the flag
workspace in HTML before JavaScript runs. Its visible learning links and answers
remain available on the overview after the interactive workspace loads.

## Public learning pages

- `/cp-and-ctf`: the combined platform, a CP/CTF comparison, and club contests.
- `/competitive-programming`: coding practice, supported languages, and verdicts.
- `/capture-the-flag`: beginner security categories and the built-in labs.
- `/problems/packet-budget`: the public problem statement and samples.
- `/problems/signal-surge`: the public problem statement and samples.

These pages are static HTML, use distinct titles/descriptions/canonical URLs, and
work without JavaScript or a running API. The sitemap lists these five pages and
the homepage. The interactive workspace continues to use hash navigation.

The content addresses searches such as “CP and CTF platform”, “competitive
programming and capture the flag”, and “CP and CTF contests”. Clear definitions,
direct answers, semantic headings, public samples, and internal links support
both regular search and answer-based discovery. Structured data describes the
actual website/application and each page's breadcrumbs, with no invented reviews
or usage figures.

The combined guide also addresses “CP and CTF merge”, “CP CTF together”,
“CP CTF merged platform”, and “CPCTF”. CPCTF is explicitly defined on the page
as shorthand for the combined practice workflow. The title, introduction,
questions, and homepage links explain this intent in readable language.

## Updating content

Edit the public learning copy in `scripts/build-search-pages.py`, then run:

```sh
python3 scripts/build-search-pages.py
```

Commit the generated HTML and `public/sitemap.xml` along with the source. Update
public statements and sample counts when changing the built-in problems. Keep
hidden tests, CTF flags, credentials, and member data out of these pages.

Update the combined page's `modified` date, or `HOME_MODIFIED` for the homepage,
when its content changes. These editorial dates populate sitemap `lastmod`;
running the generator alone keeps them unchanged.

`public/robots.txt` points to the sitemap. Vercel serves extensionless page URLs.
Admin/API responses carry `X-Robots-Tag: noindex, nofollow`; the separate backend
project has a site-wide noindex header to keep its duplicate frontend out of
search. Authentication continues to enforce administrator access.

## Google Search Console

Use the URL-prefix property `https://cyber-algo-arena.vercel.app/`. The homepage
contains the public Google ownership verification tag. Keep that tag after
verification so the property can remain verified.

Submit `sitemap.xml` in the Sitemaps report, inspect the homepage and
`/cp-and-ctf`, and request indexing when the live test succeeds. Search Console
reports crawling, indexing, impressions, and search queries as Google processes
the site. Deploying metadata or submitting a sitemap does not guarantee indexing,
ranking, a featured answer, or inclusion in an AI response.

Google's guidance:

- https://developers.google.com/search/docs/crawling-indexing/javascript/javascript-seo-basics
- https://developers.google.com/search/docs/crawling-indexing/sitemaps/build-sitemap
- https://developers.google.com/search/docs/appearance/ai-features
