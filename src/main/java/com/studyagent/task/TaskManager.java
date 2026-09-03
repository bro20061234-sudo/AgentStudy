package com.studyagent.task;

import com.studyagent.parser.StudyRequest;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Service quản lý danh sách nhiệm vụ học tập.
 * @author Thành viên 3 (Task Management)
 */
public class TaskManager {
    private List<Task> tasks;

    public TaskManager() {
        this.tasks = new ArrayList<>();
    }

    public TaskManager(List<Task> initialTasks) {
        this.tasks = initialTasks != null ? new ArrayList<>(initialTasks) : new ArrayList<>();
    }

    /**
     * Tạo Task mới từ kết quả bóc tách của AI (StudyRequest).
     */
    public Task createTaskFromRequest(StudyRequest request) {
        // TODO (TV 3):
        // 1. Kiểm tra tính hợp lệ của request (tiêu đề không được rỗng, thời lượng > 0).
        // 2. Chuyển đổi chuỗi priority thành enum Priority.
        // 3. Khởi tạo đối tượng Task mới, thêm vào danh sách và trả về.
        Priority priority = Priority.fromString(request.getPriority());
        Task task = new Task(request.getTitle(), request.getSubject(), request.getEstimatedMinutes(), request.getDeadline(), priority);
        this.tasks.add(task);
        System.out.println("✅ [TaskManager] Đã tạo nhiệm vụ mới: " + task.getTitle());
        return task;
    }

    public List<Task> getAllTasks() {
        return new ArrayList<>(tasks);
    }

    public Task findById(String taskId) {
        return tasks.stream()
                .filter(t -> t.getId().equalsIgnoreCase(taskId))
                .findFirst()
                .orElse(null);
    }

    public List<Task> getTasksByStatus(TaskStatus status) {
        // TODO (TV 3): Lọc danh sách task theo trạng thái
        return tasks.stream()
                .filter(t -> t.getStatus() == status)
                .collect(Collectors.toList());
    }

    public List<Task> getOverdueTasks() {
        // TODO (TV 3): Lọc danh sách task đã quá hạn deadline
        return tasks.stream()
                .filter(Task::isOverdue)
                .collect(Collectors.toList());
    }
}
