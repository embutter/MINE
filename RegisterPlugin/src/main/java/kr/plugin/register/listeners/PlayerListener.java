package kr.plugin.register.listeners;

import kr.plugin.register.RegisterPlugin;
import kr.plugin.register.managers.PlayerDataManager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;

/**
 * 플레이어 이벤트 처리 (접속, 퇴장, 채팅, 커맨드 차단)
 */
public class PlayerListener implements Listener {

    private final RegisterPlugin plugin;

    public PlayerListener(RegisterPlugin plugin) {
        this.plugin = plugin;
    }

    // ────────────────────────────────────────────────
    //  접속 이벤트
    // ────────────────────────────────────────────────

    @EventHandler(priority = EventPriority.NORMAL)
    public void onPlayerJoin(PlayerJoinEvent event) {
        var player = event.getPlayer();
        PlayerDataManager dm = plugin.getDataManager();

        if (dm.isRegistered(player.getUniqueId())) {
            // 등록된 플레이어: 이름 적용
            plugin.applyPlayerName(player);

            // 접속 메시지 변경 (등록 이름으로)
            String registeredName = dm.getRegisteredName(player.getUniqueId());
            event.setJoinMessage("§7[§a+§7] §a" + registeredName + " §7님이 접속하셨습니다.");

        } else {
            // 미등록 플레이어: 안내 타이틀 표시 및 강퇴 타이머 시작
            plugin.applyUnregisteredName(player);

            // 약간의 딜레이 후 타이틀 표시 (접속 직후 타이틀이 보이지 않는 문제 방지)
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                if (player.isOnline()) {
                    plugin.sendRegisterTitle(player);
                }
            }, 20L); // 1초 후

            // 안내 메시지
            player.sendMessage("");
            player.sendMessage("§e§l★ 회원가입이 필요합니다 ★");
            player.sendMessage("§f→ §e/회원가입 <이름>§f 을 입력하여 회원가입을 완료해주세요.");
            player.sendMessage("§c→ §f5분 내에 가입하지 않으면 §c§l자동 강퇴§f됩니다!");
            player.sendMessage("");

            // 강퇴 타이머 시작
            plugin.startKickTimer(player);

            // 접속 메시지
            event.setJoinMessage("§7[§c+§7] §7" + player.getName() + " §7님이 접속하셨습니다. §8(미등록)");
        }
    }

    // ────────────────────────────────────────────────
    //  퇴장 이벤트
    // ────────────────────────────────────────────────

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        var player = event.getPlayer();
        PlayerDataManager dm = plugin.getDataManager();

        // 강퇴 타이머 취소
        plugin.cancelKickTimer(player.getUniqueId());

        // 퇴장 메시지 변경
        if (dm.isRegistered(player.getUniqueId())) {
            String registeredName = dm.getRegisteredName(player.getUniqueId());
            event.setQuitMessage("§7[§c-§7] §c" + registeredName + " §7님이 퇴장하셨습니다.");
        } else {
            event.setQuitMessage("§7[§8-§7] §8" + player.getName() + " §7님이 퇴장하셨습니다. §8(미등록)");
        }
    }

    // ────────────────────────────────────────────────
    //  채팅 이벤트 (등록 이름으로 채팅)
    // ────────────────────────────────────────────────

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerChat(AsyncPlayerChatEvent event) {
        var player = event.getPlayer();
        PlayerDataManager dm = plugin.getDataManager();

        if (!dm.isRegistered(player.getUniqueId())) {
            // 미등록 플레이어 채팅 차단
            event.setCancelled(true);
            player.sendMessage("§c채팅을 이용하려면 먼저 회원가입이 필요합니다.");
            player.sendMessage("§7→ §e/회원가입 <이름>");
            return;
        }

        String registeredName = dm.getRegisteredName(player.getUniqueId());
        String chatFormat = plugin.getConfig().getString("chat-format",
                "§7[§f%name%§7] §f%message%");

        // 채팅 포맷 적용
        String formattedFormat = chatFormat
                .replace("%name%", registeredName)
                .replace("%player%", player.getName())
                .replace("%message%", "%2$s"); // Bukkit chat format 변수

        event.setFormat(formattedFormat.replace("%2$s", "%2$s")); // Bukkit 포맷 유지
    }

    // ────────────────────────────────────────────────
    //  커맨드 차단 (미등록 플레이어)
    // ────────────────────────────────────────────────

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerCommand(PlayerCommandPreprocessEvent event) {
        var player = event.getPlayer();
        PlayerDataManager dm = plugin.getDataManager();

        // 이미 등록된 플레이어는 모든 커맨드 허용
        if (dm.isRegistered(player.getUniqueId())) return;

        String message = event.getMessage().toLowerCase();

        // 허용할 커맨드 목록 (미등록 플레이어도 사용 가능)
        boolean isAllowed =
                message.startsWith("/회원가입") ||
                message.startsWith("/register") ||
                message.startsWith("/reg") ||
                message.startsWith("/login") ||  // 혹시 다른 로그인 플러그인 사용 시
                (player.hasPermission("registerplugin.admin") && message.startsWith("/reg")); // 관리자

        if (!isAllowed) {
            event.setCancelled(true);
            player.sendMessage("§c커맨드를 사용하려면 먼저 회원가입이 필요합니다.");
            player.sendMessage("§7→ §e/회원가입 <이름>");
        }
    }
}
