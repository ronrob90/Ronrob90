import axios from "axios";
import { DEMO_MODE, demoAdapter } from "./demo";

const api = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL || "",
  ...(DEMO_MODE ? { adapter: demoAdapter } : {}),
});

// Attach Bearer token to every request, EXCEPT /auth/login and /auth/register
api.interceptors.request.use((config) => {
  const token = localStorage.getItem("token");
  if (
    !DEMO_MODE && token &&
    !config.url.includes("/auth/login") &&
    !config.url.includes("/auth/register")
  ) {
    config.headers.Authorization = `Bearer ${token}`;
  }
  return config;
});

export default api;
