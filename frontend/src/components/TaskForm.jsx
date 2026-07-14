import { useState } from "react";

export default function TaskForm({ onCreate }) {
  const [description, setDescription] = useState("");
  const [date, setDate] = useState("");
  const [submitting, setSubmitting] = useState(false);

  async function handleSubmit(event) {
    event.preventDefault();
    setSubmitting(true);
    try {
      await onCreate(description, date);
      setDescription("");
      setDate("");
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <form className="task-form" onSubmit={handleSubmit}>
      <input
        type="text"
        placeholder="Task description"
        value={description}
        onChange={(event) => setDescription(event.target.value)}
        required
      />
      <input
        type="date"
        value={date}
        onChange={(event) => setDate(event.target.value)}
        required
      />
      <button type="submit" disabled={submitting}>
        Add task
      </button>
    </form>
  );
}
