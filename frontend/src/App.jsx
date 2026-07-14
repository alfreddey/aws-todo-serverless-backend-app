import { useEffect, useState } from "react";
import { Authenticator } from "@aws-amplify/ui-react";
import "@aws-amplify/ui-react/styles.css";
import TaskForm from "./components/TaskForm";
import TaskList from "./components/TaskList";
import { listTasks, createTask, updateTask, deleteTask } from "./api";
import "./App.css";

function TodoApp({ signOut, user }) {
  const [tasks, setTasks] = useState([]);
  const [activeTab, setActiveTab] = useState("Pending");
  const [error, setError] = useState(null);

  async function refreshTasks() {
    try {
      const { tasks: fetchedTasks } = await listTasks();
      setTasks(fetchedTasks);
      setError(null);
    } catch (err) {
      setError(err.message);
    }
  }

  useEffect(() => {
    refreshTasks();
  }, []);

  async function handleCreate(description, date) {
    await createTask(description, date);
    await refreshTasks();
  }

  async function handleUpdate(taskId, fields) {
    await updateTask(taskId, fields);
    await refreshTasks();
  }

  async function handleDelete(taskId) {
    await deleteTask(taskId);
    await refreshTasks();
  }

  return (
    <div className="app">
      <header>
        <h1>My Tasks</h1>
        <div>
          <span>{user?.signInDetails?.loginId}</span>
          <button onClick={signOut}>Sign out</button>
        </div>
      </header>
      {error && <p className="error">{error}</p>}
      <TaskForm onCreate={handleCreate} />
      <TaskList
        tasks={tasks}
        activeTab={activeTab}
        onTabChange={setActiveTab}
        onUpdate={handleUpdate}
        onDelete={handleDelete}
      />
    </div>
  );
}

export default function App() {
  return (
    <Authenticator>
      {({ signOut, user }) => <TodoApp signOut={signOut} user={user} />}
    </Authenticator>
  );
}
