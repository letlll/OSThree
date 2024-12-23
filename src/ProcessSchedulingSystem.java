import java.io.*;
import java.util.*;
import java.util.stream.Collectors;

public class ProcessSchedulingSystem {

    // 进程控制块（PCB）
    static class PCB {
        String proName;                   // 进程名称
        int startTime;                    // 到达时间
        int runTime;                      // 运行时间（剩余）
        int originalRunTime;             // 原始运行时间
        String programName;               // 关联的程序名称
        int firstTime;                    // 开始运行时间
        int finishTime;                   // 完成时间
        double turnoverTime;              // 周转时间
        double weightedTurnoverTime;      // 带权周转时间
        String status;                     // 状态
        List<Integer> visitList;           // 访问页号列表

        PCB(String proName, int startTime, int runTime, String programName) {
            this.proName = proName;
            this.startTime = startTime;
            this.runTime = runTime;
            this.originalRunTime = runTime;
            this.programName = programName;
            this.firstTime = -1;
            this.finishTime = 0;
            this.turnoverTime = 0.0;
            this.weightedTurnoverTime = 0.0;
            this.status = "等待";
            this.visitList = new ArrayList<>();
        }
    }

    // 运行步骤结构体
    static class RUN {
        String name;         // 进程名（程序名）
        int jumpTime;        // 执行时间
        double address;      // 访问地址

        RUN(String name, int jumpTime, double address) {
            this.name = name;
            this.jumpTime = jumpTime;
            this.address = address;
        }
    }

    // 函数信息结构体
    static class FunctionInfo {
        String funcName; // 函数名称
        double size;     // 函数大小 (KB)

        FunctionInfo(String funcName, double size) {
            this.funcName = funcName;
            this.size = size;
        }
    }

    // 程序信息结构体
    static class ProgramInfo {
        String programName;                      // 程序名称
        List<FunctionInfo> functions;            // 程序中的函数列表

        ProgramInfo(String programName) {
            this.programName = programName;
            this.functions = new ArrayList<>();
        }
    }

    // 页面替换管理器
    static class PageManager {
        double pageSize; // 页面大小（KB）
        int maxPages;    // 每个进程的最大页面数
        Queue<Integer> fifoPages; // FIFO页面队列
        LinkedHashMap<Integer, Integer> lruPages; // LRU页面映射：页面号 -> 最近访问时间
        List<String> log; // 页面操作日志
        int pageFaults;    // 缺页次数
        int pageHits;      // 命中次数

        PageManager(double pageSize, int maxPages) {
            this.pageSize = pageSize;
            this.maxPages = maxPages;
            this.fifoPages = new LinkedList<>();
            // 使用 accessOrder=true 的 LinkedHashMap 实现 LRU
            this.lruPages = new LinkedHashMap<Integer, Integer>(maxPages, 0.75f, true) {
                protected boolean removeEldestEntry(Map.Entry<Integer, Integer> eldest) {
                    if (size() > PageManager.this.maxPages) {
                        log.add("LRU: 页面 " + eldest.getKey() + " 被移除。");
                        pageFaults++;
                        return true;
                    }
                    return false;
                }
            };
            this.log = new ArrayList<>();
            this.pageFaults = 0;
            this.pageHits = 0;
        }

        // FIFO替换策略
        void fifoReplace(int page) {
            // 检查页面是否已存在
            boolean found = fifoPages.contains(page);

            if (found) {
                pageHits++;
                log.add("FIFO: 页面 " + page + " 已在内存中 (命中)。");
                displayMemoryState("FIFO");
                return;
            }

            // 页面错误
            pageFaults++;
            if (fifoPages.size() >= maxPages) {
                if (!fifoPages.isEmpty()) {
                    int removed = fifoPages.poll();
                    log.add("FIFO: 页面 " + removed + " 被移除。");
                }
            }
            fifoPages.offer(page);
            log.add("FIFO: 页面 " + page + " 被添加。");

            // 记录当前内存状态
            displayMemoryState("FIFO");
        }

        // LRU替换策略
        void lruReplace(int page, int currentTime) {
            if (lruPages.containsKey(page)) {
                pageHits++;
                lruPages.put(page, currentTime); // 更新页面最近使用时间
                log.add("LRU: 页面 " + page + " 已在内存中 (命中)。");
                displayMemoryState("LRU");
                return;
            }

            // 页面错误
            pageFaults++;
            if (lruPages.size() >= maxPages) {
                // 最久未使用的页面会被自动移除，由 LinkedHashMap 的 removeEldestEntry 方法处理
                // 这里只需记录日志
            }
            lruPages.put(page, currentTime);
            log.add("LRU: 页面 " + page + " 被添加。");

            // 记录当前内存状态
            displayMemoryState("LRU");
        }

        // 获取页面置换日志
        List<String> getLog() {
            return log;
        }

        // 获取页面错误次数
        int getPageFaults() {
            return pageFaults;
        }

        // 获取页面命中次数
        int getPageHits() {
            return pageHits;
        }

        // 计算页面命中率
        double getHitRate() {
            return (pageHits + pageFaults) == 0 ? 0 : ((double) pageHits / (pageHits + pageFaults));
        }

        // 显示当前内存中的页面状态
        void displayMemoryState(String algorithm) {
            System.out.println("当前内存状态 (" + algorithm + "):");
            System.out.print("|");
            if (algorithm.equals("FIFO")) {
                for (int page : fifoPages) {
                    System.out.printf(" %d |", page);
                }
            } else if (algorithm.equals("LRU")) {
                for (int page : lruPages.keySet()) {
                    System.out.printf(" %d |", page);
                }
            }
            System.out.println();
        }

        // 打印总结报告
        void printSummary() {
            System.out.println("缺页次数: " + pageFaults);
            System.out.println("页面命中次数: " + pageHits);
            if (pageHits + pageFaults > 0) {
                double hitRate = ((double) pageHits / (pageHits + pageFaults)) * 100;
                System.out.printf("页面命中率: %.2f%%\n", hitRate);
            }
        }
    }

    // 全局变量
    static List<PCB> processes = new ArrayList<>(); // 所有进程
    static List<RUN> runSteps = new ArrayList<>();   // 所有运行步骤
    static Map<String, ProgramInfo> programs = new HashMap<>(); // 所有程序信息

    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);

        // 读取数据文件
        readProcess("Process.txt");
        readRun("run.txt");
        readProgramDetails("program.txt");

        // 分配运行步骤到各进程的访问页号列表
        // 这一步将在分页调度时进行，因为需要用户输入页面大小

        while (true) {
            // 显示菜单
            showMenu();
            int choice = getUserChoice(scanner, 1, 7);

            switch (choice) {
                case 1:
                    // 显示进程信息
                    displayProcessInfo();
                    break;
                case 2:
                    // 显示程序详细信息
                    displayProgramDetails();
                    break;
                case 3:
                    // 先来先服务调度（FCFS）
                    performFCFS();
                    break;
                case 4:
                    // 时间片轮转调度（RR）
                    performRR(scanner);
                    break;
                case 5:
                    // 分页调度（基于访问页号）
                    PagingScheduler pagingScheduler = new PagingScheduler(4.0, 3); // 示例默认值
                    pagingScheduler.performPagingScheduling(scanner);
                    break;

                case 6:
                    // 设置页面大小并执行分页调度
                    PagingScheduler dynamicScheduler = new PagingScheduler(0.0, 0); // 动态输入
                    dynamicScheduler.performPagingScheduling(scanner);
                    break;

                case 7:
                    // 退出程序
                    System.out.println("退出程序。再见！");
                    scanner.close();
                    System.exit(0);
                    break;
                default:
                    // 无效选项
                    System.out.println("无效选项，请重新选择。");
            }
        }
    }

    // 显示菜单
    private static void showMenu() {
        System.out.println("\n===== 进程调度与分页管理系统 =====");
        System.out.println("请选择功能：");
        System.out.println("1. 显示进程信息");
        System.out.println("2. 显示程序详细信息");
        System.out.println("3. 先来先服务调度（FCFS）");
        System.out.println("4. 时间片轮转调度（RR）");
        System.out.println("5. 分页调度（基于访问页号）");
        System.out.println("6. 设置页面大小并执行分页调度");
        System.out.println("7. 退出程序");
        System.out.print("请输入选项 (1-7): ");
    }

    // 获取用户选择，并验证输入
    private static int getUserChoice(Scanner scanner, int min, int max) {
        int choice = -1;
        while (true) {
            try {
                String input = scanner.nextLine().trim();
                choice = Integer.parseInt(input);
                if (choice >= min && choice <= max) {
                    break;
                } else {
                    System.out.print("输入无效，请输入一个介于 " + min + " 和 " + max + " 之间的整数: ");
                }
            } catch (NumberFormatException e) {
                System.out.print("输入无效，请输入一个整数: ");
            }
        }
        return choice;
    }

    // 读取Process.txt文件
    private static void readProcess(String filename) {
        try (BufferedReader br = new BufferedReader(new FileReader(filename))) {
            String line;
            while ((line = br.readLine()) != null) {
                if (line.trim().isEmpty()) continue;
                String[] parts = line.trim().split("\\s+");
                if (parts.length < 4) {
                    System.err.println("警告: Process.txt 中的行格式不正确: " + line);
                    continue;
                }
                String proName = parts[0];
                int startTime = Integer.parseInt(parts[1]);
                int runTime = Integer.parseInt(parts[2]);
                String programName = parts[3];
                PCB pcb = new PCB(proName, startTime, runTime, programName);
                processes.add(pcb);
            }
        } catch (IOException e) {
            System.err.println("Error: 无法读取 " + filename + " 文件。");
            System.exit(1);
        }
    }

    // 读取run.txt文件
    private static void readRun(String filename) {
        try (BufferedReader br = new BufferedReader(new FileReader(filename))) {
            String line;
            String currentProgram = "";
            while ((line = br.readLine()) != null) {
                if (line.trim().isEmpty()) continue;
                // 检测是否为程序名行
                if (line.startsWith("program")) {
                    currentProgram = line.trim();
                    continue;
                }
                String[] parts = line.trim().split("\\s+");
                if (parts.length < 3) {
                    System.err.println("警告: run.txt 中的行格式不正确: " + line);
                    continue;
                }
                int jumpTime = Integer.parseInt(parts[0]);
                String operation = parts[1];
                double address = Double.parseDouble(parts[2]);
                if (operation.equals("结束")) {
                    address = -1;
                }
                RUN run = new RUN(currentProgram, jumpTime, address);
                runSteps.add(run);
            }
        } catch (IOException e) {
            System.err.println("Error: 无法读取 " + filename + " 文件。");
            System.exit(1);
        }
    }

    // 读取program.txt文件
    private static void readProgramDetails(String filename) {
        try (BufferedReader br = new BufferedReader(new FileReader(filename))) {
            String line;
            String currentProgram = "";
            while ((line = br.readLine()) != null) {
                if (line.trim().isEmpty()) continue;
                // 检测是否为FName行
                if (line.startsWith("FName")) {
                    String[] parts = line.trim().split("\\s+");
                    if (parts.length < 2) {
                        System.err.println("警告: program.txt 中的FName行格式不正确: " + line);
                        continue;
                    }
                    currentProgram = parts[1];
                    programs.put(currentProgram, new ProgramInfo(currentProgram));
                    continue;
                }
                String[] parts = line.trim().split("\\s+");
                if (parts.length < 2) {
                    System.err.println("警告: program.txt 中的行格式不正确: " + line);
                    continue;
                }
                String funcName = parts[0];
                double size = Double.parseDouble(parts[1]);
                FunctionInfo func = new FunctionInfo(funcName, size);
                if (currentProgram.isEmpty()) {
                    System.err.println("警告: program.txt 中的函数未关联到任何程序: " + line);
                    continue;
                }
                programs.get(currentProgram).functions.add(func);
            }
        } catch (IOException e) {
            System.err.println("Error: 无法读取 " + filename + " 文件。");
            System.exit(1);
        }
    }

    // 显示进程信息
    private static void displayProcessInfo() {
        System.out.println("\n===== 进程信息 =====");
        System.out.printf("%-12s%-12s%-12s%-15s%-15s\n", "进程名", "到达时间", "运行时间", "程序名称", "状态");
        System.out.println("------------------------------------------------------------------");
        for (PCB pro : processes) {
            System.out.printf("%-12s%-12d%-12d%-15s%-15s\n",
                    pro.proName,
                    pro.startTime,
                    pro.originalRunTime,
                    pro.programName,
                    pro.status);
        }
    }

    // 显示程序详细信息
    private static void displayProgramDetails() {
        System.out.println("\n===== 程序详细信息 =====");
        for (ProgramInfo prog : programs.values()) {
            System.out.println("程序: " + prog.programName);
            for (FunctionInfo func : prog.functions) {
                System.out.println("  函数: " + func.funcName + ", 大小: " + func.size + " KB");
            }
            System.out.println();
        }
    }

    // 先来先服务调度（FCFS）
    private static void performFCFS() {
        System.out.println("\n=== 先来先服务调度（FCFS） ===");
        // 按照到达时间排序
        List<PCB> sortedProcesses = new ArrayList<>(processes);
        sortedProcesses.sort(Comparator.comparingInt(p -> p.startTime));

        int currentTime = 0;
        for (PCB pro : sortedProcesses) {
            if (currentTime < pro.startTime) {
                currentTime = pro.startTime;
            }
            pro.firstTime = currentTime;
            pro.status = "执行";
            currentTime += pro.runTime;
            pro.finishTime = currentTime;
            pro.turnoverTime = pro.finishTime - pro.startTime;
            if (pro.originalRunTime > 0) {
                pro.weightedTurnoverTime = (double) pro.turnoverTime / pro.originalRunTime;
            } else {
                pro.weightedTurnoverTime = 0.0;
            }
            pro.status = "完成";
        }

        // 输出结果到result.txt
        try (BufferedWriter bw = new BufferedWriter(new FileWriter("result.txt", true))) {
            bw.write("\n=== 先来先服务调度（FCFS） ===\n");
            bw.write("进程名\t到达时间\t运行时间\t开始时间\t完成时间\t周转时间\t带权周转时间\n");
            System.out.printf("\n=== 先来先服务调度（FCFS） ===\n");
            System.out.printf("%-10s%-12s%-12s%-12s%-12s%-12s%-16s\n",
                    "进程名", "到达时间", "运行时间", "开始时间", "完成时间", "周转时间", "带权周转时间");
            System.out.println("--------------------------------------------------------------------------");

            for (PCB pro : sortedProcesses) {
                bw.write(String.format("%s\t%d\t%d\t%d\t%d\t%.0f\t%.2f\n",
                        pro.proName,
                        pro.startTime,
                        pro.originalRunTime,
                        pro.firstTime,
                        pro.finishTime,
                        pro.turnoverTime,
                        pro.weightedTurnoverTime));

                System.out.printf("%-10s%-12d%-12d%-12d%-12d%-12.0f%-16.2f\n",
                        pro.proName,
                        pro.startTime,
                        pro.originalRunTime,
                        pro.firstTime,
                        pro.finishTime,
                        pro.turnoverTime,
                        pro.weightedTurnoverTime);
            }
        } catch (IOException e) {
            System.err.println("Error: 无法写入 result.txt 文件。");
        }

        System.out.println("先来先服务调度（FCFS）完成。结果已保存到 result.txt\n");
    }

    // 时间片轮转调度（RR）
    private static void performRR(Scanner scanner) {
        System.out.println("\n=== 时间片轮转调度（RR） ===");
        System.out.print("请输入时间片长度 (ms): ");
        int timeQuantum = getUserChoice(scanner, 1, Integer.MAX_VALUE);

        // 按照到达时间排序
        List<PCB> sortedProcesses = new ArrayList<>(processes);
        sortedProcesses.sort(Comparator.comparingInt(p -> p.startTime));

        // 初始化就绪队列
        Queue<PCB> readyQueue = new LinkedList<>();
        int currentTime = 0;
        int index = 0;
        int n = sortedProcesses.size();

        // 将所有到达时间 <= currentTime 的进程加入就绪队列
        while (index < n && sortedProcesses.get(index).startTime <= currentTime) {
            readyQueue.offer(sortedProcesses.get(index));
            sortedProcesses.get(index).status = "就绪";
            index++;
        }

        while (!readyQueue.isEmpty()) {
            PCB currentProcess = readyQueue.poll();

            // 记录开始时间
            if (currentProcess.firstTime == -1) {
                currentProcess.firstTime = currentTime;
            }

            currentProcess.status = "执行";

            // 执行时间
            int execTime = Math.min(timeQuantum, currentProcess.runTime);
            currentTime += execTime;
            currentProcess.runTime -= execTime;

            // 模拟页面访问（可根据实际需求调整）

            // 检查是否有新进程到达
            while (index < n && sortedProcesses.get(index).startTime <= currentTime) {
                readyQueue.offer(sortedProcesses.get(index));
                sortedProcesses.get(index).status = "就绪";
                index++;
            }

            if (currentProcess.runTime > 0) {
                readyQueue.offer(currentProcess);
                currentProcess.status = "就绪";
            } else {
                currentProcess.finishTime = currentTime;
                currentProcess.turnoverTime = currentProcess.finishTime - currentProcess.startTime;
                if (currentProcess.originalRunTime > 0) {
                    currentProcess.weightedTurnoverTime = (double) currentProcess.turnoverTime / currentProcess.originalRunTime;
                } else {
                    currentProcess.weightedTurnoverTime = 0.0;
                }
                currentProcess.status = "完成";
            }
        }

        // 输出结果到result.txt
        try (BufferedWriter bw = new BufferedWriter(new FileWriter("result.txt", true))) {
            bw.write("\n=== 时间片轮转调度（RR） ===\n");
            bw.write("进程名\t到达时间\t运行时间\t开始时间\t完成时间\t周转时间\t带权周转时间\n");
            System.out.printf("\n=== 时间片轮转调度（RR） ===\n");
            System.out.printf("%-10s%-12s%-12s%-12s%-12s%-12s%-16s\n",
                    "进程名", "到达时间", "运行时间", "开始时间", "完成时间", "周转时间", "带权周转时间");
            System.out.println("--------------------------------------------------------------------------");

            for (PCB pro : sortedProcesses) {
                bw.write(String.format("%s\t%d\t%d\t%d\t%d\t%.0f\t%.2f\n",
                        pro.proName,
                        pro.startTime,
                        pro.originalRunTime,
                        pro.firstTime,
                        pro.finishTime,
                        pro.turnoverTime,
                        pro.weightedTurnoverTime));

                System.out.printf("%-10s%-12d%-12d%-12d%-12d%-12.0f%-16.2f\n",
                        pro.proName,
                        pro.startTime,
                        pro.originalRunTime,
                        pro.firstTime,
                        pro.finishTime,
                        pro.turnoverTime,
                        pro.weightedTurnoverTime);
            }
        } catch (IOException e) {
            System.err.println("Error: 无法写入 result.txt 文件。");
        }

        System.out.println("时间片轮转调度（RR）完成。结果已保存到 result.txt\n");
    }

    // Static class for Paging Scheduling
    static class PagingScheduler {

        private PageManager pageManager;

        public PagingScheduler(double pageSize, int maxPages) {
            this.pageManager = new PageManager(pageSize, maxPages);
        }

        // Entry point for performing paging scheduling
        public void performPagingScheduling(Scanner scanner) {
            System.out.println("\n===== 设置页面大小并执行分页调度 =====");

            // Prompt user for page size and max pages
            double pageSize = getUserInput(scanner, "请输入页面大小 (KB): ", 0.1, Double.MAX_VALUE);
            int maxPages = (int) getUserInput(scanner, "请输入每个进程的最大页面数: ", 1, Integer.MAX_VALUE);

            // Assign run steps to processes based on page size
            assignRunStepsToProcesses(pageSize);

            // Calculate page requirements for all programs
            Map<String, Integer> pageRequirements = calculatePageRequirements(pageSize);

            // Prompt user for page replacement algorithm choice
            System.out.println("请选择页面调度算法：\n1. FIFO\n2. LRU");
            int choice = (int) getUserInput(scanner, "请输入选择 (1 或 2): ", 1, 2);

            pageManager = new PageManager(pageSize, maxPages);

            // Perform page scheduling based on the selected algorithm
            System.out.println("\n页面调度过程:");
            performPageReplacement(choice, pageRequirements);

            // Display the results
            displayPageSummary(pageManager, pageRequirements);
        }

        // Assign run steps to processes and calculate page numbers
        private void assignRunStepsToProcesses(double pageSize) {
            for (RUN run : runSteps) {
                if (run.address == -1) {
                    continue; // Ignore "end" operations
                }
                int pageNumber = (int) Math.floor(run.address / pageSize);

                // Assign page numbers to the corresponding process
                processes.stream()
                        .filter(pro -> pro.programName.equals(run.name))
                        .findFirst()
                        .ifPresent(pro -> pro.visitList.add(pageNumber));
            }
        }

        // Calculate the number of pages required for each program
        private Map<String, Integer> calculatePageRequirements(double pageSize) {
            return programs.entrySet().stream()
                    .collect(Collectors.toMap(
                            Map.Entry::getKey,
                            entry -> (int) Math.ceil(
                                    entry.getValue().functions.stream()
                                            .mapToDouble(func -> func.size)
                                            .sum() / pageSize
                            )
                    ));
        }

        // Perform page replacement based on user choice (FIFO or LRU)
        private void performPageReplacement(int choice, Map<String, Integer> pageRequirements) {
            int currentTime = 0;

            for (Map.Entry<String, Integer> entry : pageRequirements.entrySet()) {
                String programName = entry.getKey();
                int pages = entry.getValue();
                System.out.printf("程序 %s 需要 %d 页\n", programName, pages);

                for (int page = 0; page < pages; page++) {
                    if (choice == 1) {
                        pageManager.fifoReplace(page);
                    } else {
                        pageManager.lruReplace(page, currentTime);
                    }
                    currentTime++;
                }
            }
        }

        // Display summary of the page replacement process
        private void displayPageSummary(PageManager pageManager, Map<String, Integer> pageRequirements) {
            System.out.println("\n===== 分页调度总结报告 =====");
            pageRequirements.forEach((programName, pages) -> {
                System.out.printf("程序: %s | 总页面数: %d\n", programName, pages);
            });

            System.out.println("页面命中次数: " + pageManager.getPageHits());
            System.out.println("页面置换次数 (页面错误): " + pageManager.getPageFaults());
            System.out.printf("页面命中率: %.2f%%\n", pageManager.getHitRate() * 100);
        }

        // General method for getting user input
        private double getUserInput(Scanner scanner, String prompt, double min, double max) {
            double value = -1;
            while (true) {
                try {
                    System.out.print(prompt);
                    value = Double.parseDouble(scanner.nextLine().trim());
                    if (value >= min && value <= max) {
                        break;
                    } else {
                        System.out.printf("输入无效，请输入一个介于 %.1f 和 %.1f 之间的数字。\n", min, max);
                    }
                } catch (NumberFormatException e) {
                    System.out.println("输入无效，请输入一个数字。");
                }
            }
            return value;
        }
    }
}