import { fetchAuthSession } from "aws-amplify/auth";

const API_URL = import.meta.env.VITE_API_URL;

async function authorizedFetch(path, options = {}) {
  const session = await fetchAuthSession();
  const idToken = session.tokens?.idToken?.toString();

  const response = await fetch(`${API_URL}${path}`, {
    ...options,
    headers: {
      "Content-Type": "application/json",
      Authorization: idToken,
      ...options.headers,
    },
  });

  if (!response.ok) {
    const body = await response.json().catch(() => ({}));
    throw new Error(body.message || `Request failed with status ${response.status}`);
  }

  return response.status === 204 ? null : response.json();
}

export const listTasks = () => authorizedFetch("/tasks");

export const createTask = (description, date) =>
  authorizedFetch("/tasks", {
    method: "POST",
    body: JSON.stringify({ description, date }),
  });

export const updateTask = (taskId, fields) =>
  authorizedFetch(`/tasks/${taskId}`, {
    method: "PUT",
    body: JSON.stringify(fields),
  });

export const deleteTask = (taskId) =>
  authorizedFetch(`/tasks/${taskId}`, { method: "DELETE" });
