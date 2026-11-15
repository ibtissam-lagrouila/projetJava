/** @type {import('tailwindcss').Config} */
export default {
  content: [
    "./index.html",
    "./src/**/*.{js,ts,jsx,tsx}",
  ],
  theme: {
    extend: {
      colors: {
        fitwell: {
          burgundy: '#5B1421',
          'burgundy-dark': '#48101A',
          'burgundy-light': '#721B2B',
          btn: '#791E2D',
          'btn-hover': '#631724',
          sand: '#D3B996',
          card: '#E2CEB0',
          'card-hover': '#DBC5A5',
          input: '#DFCAAC',
          tag: '#ECD9C2',
          text: '#241814',
          muted: '#66554D',
          cream: '#F6F0E6',
          border: 'rgba(80, 50, 30, 0.15)',
        }
      },
      fontFamily: {
        serif: ['Newsreader', 'Playfair Display', 'Georgia', 'serif'],
        sans: ['"Plus Jakarta Sans"', 'Inter', 'system-ui', 'sans-serif'],
      },
      borderRadius: {
        'card': '16px',
        'pill': '9999px',
      }
    },
  },
  plugins: [],
}

