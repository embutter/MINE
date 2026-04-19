package kr.plugin.register.commands;

import kr.plugin.register.RegisterPlugin;
import kr.plugin.register.managers.PlayerDataManager;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;

/**
 * /회원가입 <이름> 커맨드 처리
 */
public class RegisterCommand implements CommandExecutor, TabCompleter {

    private final RegisterPlugin plugin;

    public RegisterCommand(RegisterPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {

        // 플레이어만 사용 가능
        if (!(sender instanceof Player player)) {
            sender.sendMessage(plugin.getMsg("not-player"));
            return true;
        }

        PlayerDataManager dm = plugin.getDataManager();

        // 이미 등록된 경우
        if (dm.isRegistered(player.getUniqueId())) {
            player.sendMessage(plugin.getMsg("register-already"));
            return true;
        }

        // 인자 확인
        if (args.length < 1) {
            player.sendMessage(plugin.getMsg("register-usage"));
            player.sendTitle(
                    plugin.getConfig().getString("title.main", "§e§l/회원가입 §f<이름 입력>"),
                    plugin.getConfig().getString("title.sub",  "§c§l방치할 경우 5분 후 강퇴됩니다!"),
                    10, 80, 10
            );
            return true;
        }

        String name = args[0];

        // 이름 유효성 검사
        String validationError = validateName(name, player, null);
        if (validationError != null) {
            player.sendMessage(validationError);
            return true;
        }

        // 이름 중복 확인
        if (dm.isNameTaken(name)) {
            player.sendMessage(plugin.getMsg("register-name-taken"));
            return true;
        }

        // 회원가입 처리
        boolean success = dm.registerPlayer(player.getUniqueId(), name);
        if (!success) {
            // 등록 실패 (이미 등록됨, 위에서 체크했지만 혹시 모를 경우)
            player.sendMessage(plugin.getMsg("register-already"));
            return true;
        }

        // 강퇴 타이머 취소
        plugin.cancelKickTimer(player.getUniqueId());

        // 이름 적용 (디스플레이 네임, 탭 목록)
        plugin.applyPlayerName(player);

        // 성공 메시지
        String successMsg = plugin.getMsg("register-success").replace("%name%", name);
        player.sendMessage(successMsg);

        // 성공 타이틀
        player.sendTitle("§a§l회원가입 완료!", "§f이름: §e" + name, 10, 60, 10);

        // 로그
        plugin.getLogger().info("[회원가입] " + player.getName() + " -> " + name);

        return true;
    }

    /**
     * 이름 유효성 검사
     * @param name 검사할 이름
     * @param player 플레이어 (메시지 전송용)
     * @param currentName 현재 이름 (이름변경 시 본인 이름 제외용, null = 회원가입)
     * @return null = 유효, String = 오류 메시지
     */
    public String validateName(String name, Player player, String currentName) {
        int minLen = plugin.getConfig().getInt("name.min-length", 2);
        int maxLen = plugin.getConfig().getInt("name.max-length", 16);
        String forbiddenRegex = plugin.getConfig().getString("name.forbidden-regex", "[^a-zA-Z0-9가-힣_]");

        if (name.length() < minLen) {
            return plugin.getMsg("register-name-short")
                    .replace("2글자", minLen + "글자");
        }
        if (name.length() > maxLen) {
            return plugin.getMsg("register-name-long")
                    .replace("16글자", maxLen + "글자");
        }
        if (!forbiddenRegex.isEmpty() && name.matches(".*" + forbiddenRegex + ".*")) {
            return plugin.getMsg("register-name-invalid");
        }
        return null;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command,
                                      String alias, String[] args) {
        if (args.length == 1) {
            return Collections.singletonList("<이름>");
        }
        return Collections.emptyList();
    }
}
