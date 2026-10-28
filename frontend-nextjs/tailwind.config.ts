import type { Config } from "tailwindcss";

/** Design tokens from architecture/05_UI_UX_DESIGN.md section 8. */
const config: Config = {
  content: ["./app/**/*.{ts,tsx}", "./components/**/*.{ts,tsx}"],
  theme: {
    extend: {
      colors: {
        primary: "#2A9D8F",
        primaryDark: "#228176",
        sage: "#A7C4BC",
        calm: "#F7F9F9",
        slateDark: "#1E2A32",
        slateDeep: "#141D23",
        amberWarm: "#E9C46A",
        charcoal: "#2D3748",
        mist: "#718096",
        coral: "#E76F51",
      },
      fontFamily: {
        display: ["var(--font-poppins)", "system-ui", "sans-serif"],
        sans: ["var(--font-inter)", "system-ui", "-apple-system", "sans-serif"],
      },
    },
  },
  plugins: [],
};

export default config;
