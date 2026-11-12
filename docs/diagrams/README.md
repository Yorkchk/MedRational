# Diagrams

The main README shows these diagrams as SVG images. Each one is generated from the [Mermaid](https://mermaid.js.org) file with the same name, so edit the `.mmd` file and then regenerate its SVG:

```bash
npx -y @mermaid-js/mermaid-cli@11.4.2 -i architecture.mmd -o architecture.svg -b white \
  -c mermaid-config.json
```

`mermaid-config.json` turns off HTML labels, so the text is plain SVG and shows in every Markdown viewer, not only in browsers. The white background keeps the diagrams readable in GitHub's dark mode.

If `npx` can't download Chromium, point it at an installed Chrome or Edge with `-p puppeteer.json`, where `puppeteer.json` is `{"executablePath": "C:/Program Files/Google/Chrome/Application/chrome.exe", "headless": "shell"}`.
