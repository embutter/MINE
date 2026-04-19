package kr.plugin.register;

import kr.plugin.register.commands.NameChangeCommand;
import kr.plugin.register.commands.RegisterCommand;
import kr.plugin.register.listeners.PlayerListener;
import kr.plugin.register.managers.PlayerDataManager;
import kr.plugin.register.managers.TabManager;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class RegisterPlugin extends JavaPlugin {

    private static RegisterPlugin instance;

    private PlayerDataManager dataManager;
    private TabManager tabManager;

    // 미등록 플레이어의 강퇴 타이머 Map<UUID, Task>
    private final Map<UUID, BukkitTask> kickTasks = new HashMap<>();

    @Override
    public void onEnable() {
        instance = this;

        // 설정 파일 저장/로드
        saveDefaultConfig();

        // 데이터 매니저 초기화
        dataManager = new PlayerDataManager(this);

        // TAB 매니저 초기화 (TAB 플러그인 soft-depend)
        tabManager = new TabManager(this);

        // 커맨드 등록
        RegisterCommand registerCmd = new RegisterCommand(this);
        getCommand("회원가입").setExecutor(registerCmd);
        getCommand("회원가입").setTabCompleter(registerCmd);

        NameChangeCommand renameCmd = new NameChangeCommand(this);
        getCommand("이름변경").setExecutor(renameCmd);
        getCommand("이름변경").setTabCompleter(renameCmd);

        // 이벤트 리스너 등록
        getServer().getPluginManager().registerEvents(new PlayerListener(this), this);

        // 타이틀 반복 알림 스케줄러
        startTitleRepeatScheduler();

        // 서버 재시작 시 이미 접속 중인 플레이어 처리
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!dataManager.isRegistered(player.getUniqueId())) {
                startKickTimer(player);
                sendRegisterTitle(player);
            } else {
                applyPlayerName(player);
            }
        }

        getLogger().info("RegisterPlugin 이 활성화되었습니다!");
        getLogger().info("TAB 플러그인 연동: " + (tabManager.isTabEnabled() ? "활성화" : "비활성화"));
    }

    @Override
    public void onDisable() {
        // 모든 강퇴 타이머 취소
        kickTasks.values().forEach(BukkitTask::cancel);
        kickTasks.clear();

        // 데이터 저장
        if (dataManager != null) {
            dataManager.saveAll();
        }

        getLogger().info("RegisterPlugin 이 비활성화되었습니다.");
    }

    // ────────────────────────────────────────────────
    //  강퇴 타이머
    // ────────────────────────────────────────────────

    /**
     * 미등록 플레이어에게 강퇴 타이머를 시작합니다.
     */
    public void startKickTimer(Player player) {
        cancelKickTimer(player.getUniqueId()); // 기존 타이머 제거

        int delaySeconds = getConfig().getInt("kick-delay-seconds", 300);
        long delayTicks = delaySeconds * 20L;

        BukkitTask task = Bukkit.getScheduler().runTaskLater(this, () -> {
            if (player.isOnline() && !dataManager.isRegistered(player.getUniqueId())) {
                String kickMsg = getConfig().getString("kick-message",
                        "§c회원가입을 하지 않아 강퇴되었습니다.");
                player.kickPlayer(kickMsg);
                getLogger().info("[강퇴] " + player.getName() + " - 미등록 5분 초과");
            }
            kickTasks.remove(player.getUniqueId());
        }, delayTicks);

        kickTasks.put(player.getUniqueId(), task);
    }

    /**
     * 플레이어의 강퇴 타이머를 취소합니다. (회원가입 완료 시)
     */
    public void cancelKickTimer(UUID uuid) {
        BukkitTask task = kickTasks.remove(uuid);
        if (task != null) {
            task.cancel();
        }
    }

    public boolean hasKickTimer(UUID uuid) {
        return kickTasks.containsKey(uuid);
    }

    // ────────────────────────────────────────────────
    //  타이틀 표시
    // ────────────────────────────────────────────────

    /**
     * 미등록 플레이어에게 회원가입 안내 타이틀을 표시합니다.
     */
    public void sendRegisterTitle(Player player) {
        String main  = getConfig().getString("title.main",  "§e§l/회원가입 §f<이름 입력>");
        String sub   = getConfig().getString("title.sub",   "§c§l방치할 경우 5분 후 강퇴됩니다!");
        int fadeIn   = getConfig().getInt("title.fade-in",  20);
        int stay     = getConfig().getInt("title.stay",     200);
        int fadeOut  = getConfig().getInt("title.fade-out", 20);

        player.sendTitle(main, sub, fadeIn, stay, fadeOut);
    }

    /**
     * 타이틀 반복 알림 스케줄러를 시작합니다.
     */
    private void startTitleRepeatScheduler() {
        int interval = getConfig().getInt("title-repeat-interval", 60);
        if (interval <= 0) return;

        long intervalTicks = interval * 20L;
        Bukkit.getScheduler().runTaskTimer(this, () -> {
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (!dataManager.isRegistered(player.getUniqueId())) {
                    sendRegisterTitle(player);
                }
            }
        }, intervalTicks, intervalTicks);
    }

    // ────────────────────────────────────────────────
    //  플레이어 이름 적용
    // ────────────────────────────────────────────────

    /**
     * 등록된 플레이어에게 이름을 적용합니다.
     * - 디스플레이 네임, 탭 리스트 이름, TAB 플러그인 이름 설정
     */
    public void applyPlayerName(Player player) {
        String registeredName = dataManager.getRegisteredName(player.getUniqueId());
        if (registeredName == null) return;

        // 디스플레이 네임 (채팅, 네임태그)
        player.setDisplayName("§a" + registeredName);
        player.setPlayerListName("§a" + registeredName);

        // TAB 플러그인 연동
        tabManager.updateTabName(player, registeredName);
    }

    /**
     * 미등록 플레이어에게 탭 목록 이름을 적용합니다.
     */
    public void applyUnregisteredName(Player player) {
        String format = getConfig().getString("tab-plugin.unregistered-format",
                "§c[미등록] §7%player%")
                .replace("%player%", player.getName());

        player.setPlayerListName(format);
        tabManager.updateUnregisteredName(player);
    }

    // ────────────────────────────────────────────────
    //  유틸리티 메서드
    // ────────────────────────────────────────────────

    public String getMsg(String key) {
        String prefix = getConfig().getString("messages.prefix", "§8[§b회원가입§8] §r");
        String msg    = getConfig().getString("messages." + key, "§cMessage not found: " + key);
        return prefix + msg;
    }

    public String getMsgRaw(String key) {
        return getConfig().getString("messages." + key, "§cMessage not found: " + key);
    }

    // ────────────────────────────────────────────────
    //  Getter
    // ────────────────────────────────────────────────

    public static RegisterPlugin getInstance() { return instance; }
    public PlayerDataManager getDataManager()  { return dataManager; }
    public TabManager getTabManager()          { return tabManager; }
}
