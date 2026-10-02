# Branding

Place the company logo here as `logo.png`. It is printed in the top-left corner of the reports
(delivery, loan and supplier order PDFs).

- To change the logo, replace `logo.png` with the new file, keeping the same name. No code change,
  rebuild or restart is needed: the file is read every time a report is generated.
- Recommended: PNG with transparent background, landscape, around 600 x 200 px.
  It is scaled to fit 140 x 50 points.
- Without `logo.png`, the company name (`app.report.company-name`, env `COMPANY_NAME`) is printed instead.
- Another location can be set with the `LOGO_PATH` environment variable (e.g. in production).
