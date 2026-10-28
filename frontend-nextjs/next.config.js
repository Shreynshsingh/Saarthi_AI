/** @type {import('next').NextConfig} */
const apiHostname = (() => {
  try {
    return new URL(process.env.NEXT_PUBLIC_API_URL ?? "http://localhost:8080").hostname;
  } catch {
    return "localhost";
  }
})();

const nextConfig = {
  reactStrictMode: true,
  images: {
    // Mentor photos are served by the Java backend, same host as the API.
    remotePatterns: [
      { protocol: "http", hostname: apiHostname },
      { protocol: "https", hostname: apiHostname },
    ],
  },
};

module.exports = nextConfig;
