/** @type {import('tailwindcss').Config} */
module.exports = {
  content: ['./src/**/*.{html,ts}'],
  theme: {
    extend: {
      fontFamily: {
        sans: ['"Space Grotesk"', 'system-ui', 'sans-serif'],
        mono: ['"JetBrains Mono"', 'monospace'],
      },
      colors: {
        surface: {
          bg: '#F4F1EA',
          card: '#FAF8F3',
          border: '#DED8CB',
          divider: '#E4DFD3',
        },
        ink: {
          DEFAULT: '#14120E',
          muted: '#7A756A',
          subtle: '#A39C8E',
          faint: '#CFC8B9',
        },
        accent: '#FACC15',
        status: {
          ok: '#2F7D5A',
          warn: '#A8621A',
          info: '#3B6EA8',
        },
      },
    },
  },
  plugins: [],
};
