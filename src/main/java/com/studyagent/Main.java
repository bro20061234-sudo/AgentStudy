package com.studyagent;

import com.studyagent.agent.StudyAgent;
import com.studyagent.task.Task;

import java.util.List;
import java.util.Scanner;

/**
 * Điểm khởi chạy chương trình & Giao diện dòng lệnh tương tác (CLI).
 * @author Thành viên 1 (Leader / CLI)
 */
public class Main {
    public static void main(String[] args) {
        System.out.println("=================================================");
        System.out.println("       🎓 CHÀO MỪNG ĐẾN VỚI SMART STUDY AGENT     ");
        System.out.println("        Trợ lý học tập thông minh bằng Java      ");
        System.out.println("=================================================");

        StudyAgent agent = new StudyAgent();
        Scanner scanner = new Scanner(System.in);

        // Chạy thử nghiệm 1 flow mẫu mặc định
        System.out.println("\n[1/2] Đang chạy kiểm thử luồng mẫu tự động...");
        agent.processNaturalLanguageInput("Học 2 tiếng ôn thi OOP Java trước 20h ngày mai");

        // Menu tương tác với người dùng
        while (true) {
            System.out.println("\n--- MENU TRỢ LÝ HỌC TẬP ---");
            System.out.println("1. Nhập yêu cầu học tập bằng câu tự nhiên (AI)");
            System.out.println("2. Xem danh sách tất cả nhiệm vụ đã lưu");
            System.out.println("0. Thoát chương trình");
            System.out.print("👉 Lựa chọn của bạn: ");

            String choice = scanner.nextLine().trim();
            if (choice.equals("0")) {
                System.out.println("👋 Tạm biệt và chúc bạn học tập hiệu quả!");
                break;
            } else if (choice.equals("1")) {
                System.out.print("Nhập câu lệnh của bạn (VD: 'Làm bài tập CSDL 3 tiếng tối nay'): ");
                String input = scanner.nextLine();
                if (!input.trim().isEmpty()) {
                    agent.processNaturalLanguageInput(input);
                }
            } else if (choice.equals("2")) {
                List<Task> tasks = agent.getAllTasks();
                System.out.println("\n📋 DANH SÁCH NHIỆM VỤ HIỆN CÓ (" + tasks.size() + "):");
                if (tasks.isEmpty()) {
                    System.out.println("  (Chưa có nhiệm vụ nào được lưu)");
                } else {
                    for (Task t : tasks) {
                        System.out.println("  • " + t);
                    }
                }
            } else {
                System.out.println("⚠️ Lựa chọn không hợp lệ, vui lòng thử lại!");
            }
        }

        scanner.close();
    }
}
