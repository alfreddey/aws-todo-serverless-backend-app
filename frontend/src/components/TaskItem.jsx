import { useState } from "react";

export default function TaskItem({ task, onUpdate, onDelete }) {
  const [isEditing, setIsEditing] = useState(false);
  const [description, setDescription] = useState(task.Description);
  const [date, setDate] = useState(task.Date);

  async function handleSave() {
    await onUpdate(task.TaskId, { description, date });
    setIsEditing(false);
  }

  if (isEditing) {
    return (
      <li className="task-item">
        <input value={description} onChange={(event) => setDescription(event.target.value)} />
        <input type="date" value={date} onChange={(event) => setDate(event.target.value)} />
        <button onClick={handleSave}>Save</button>
        <button onClick={() => setIsEditing(false)}>Cancel</button>
      </li>
    );
  }

  return (
    <li className="task-item">
      <div className="task-details">
        <strong>{task.Description}</strong>
        <span>{task.Date}</span>
        <span className={`status status-${task.Status.toLowerCase()}`}>{task.Status}</span>
      </div>
      <div className="task-actions">
        {task.Status === "Pending" && (
          <>
            <button onClick={() => onUpdate(task.TaskId, { status: "Completed" })}>Complete</button>
            <button onClick={() => setIsEditing(true)}>Edit</button>
          </>
        )}
        <button onClick={() => onDelete(task.TaskId)}>Delete</button>
      </div>
    </li>
  );
}
