### ✌️ kitshn 2.1.6

#### 🐛 Fixed
- Fixed sign-in failing on iOS (#458)
- Picking a recipe in a meal plan no longer messes up the servings (#451, #454 — thanks to @joeShuff)
- Social media (TikTok/Instagram) AI import no longer fails on certain thumbnails

---

<details><summary>Full commit list</summary>

```
- fix(api/auth): django-allauth cookie parsing issue on iOS (resolved #458)
- fix(import): tidy social-media thumbnail helper
- fix(import): handle data: thumbnails in social-media AI import
- fix: recipeID going to servings (resolved #451) (#454)
```

</details>

**Full Changelog**: https://github.com/aimok04/kitshn/compare/v2.1.5...v2.1.6
