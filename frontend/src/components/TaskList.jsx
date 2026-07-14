import TaskItem from "./TaskItem";

const STATUS_TABS = ["Pending", "Completed", "Expired"];

export default function TaskList({ tasks, activeTab, onTabChange, onUpdate, onDelete }) {
  const filteredTasks = tasks.filter((task) => task.Status === activeTab);

  return (
    <div className="task-list">
      <div className="tabs">
        {STATUS_TABS.map((tab) => (
          <button
            key={tab}
            className={tab === activeTab ? "tab active" : "tab"}
            onClick={() => onTabChange(tab)}
          >
            {tab} ({tasks.filter((task) => task.Status === tab).length})
          </button>
        ))}
      </div>
      {filteredTasks.length === 0 ? (
        <p className="empty-state">No {activeTab.toLowerCase()} tasks.</p>
      ) : (
        <ul>
          {filteredTasks.map((task) => (
            <TaskItem key={task.TaskId} task={task} onUpdate={onUpdate} onDelete={onDelete} />
          ))}
        </ul>
      )}
    </div>
  );
}
