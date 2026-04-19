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

/**
 * /이름변경 <새이름> 커맨드 처리
 */
public class NameChangeCommand implements CommandExecutor, TabCompleter {

    private final RegisterPlugin plugin;

    public NameChangeCommand(RegisterPlugin plugin) {
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

        // 회원가입 여부 확인
        if (!dm.isRegistered(player.getUniqueId())) {
            player.sendMessage(plugin.getMsg("rename-not-registered"));
            return true;
        }

        // 인자 확인
        if (args.length < 1) {
            String currentName = dm.getRegisteredName(player.getUniqueId());
            player.sendMessage(plugin.getMsg("rename-usage"));
            player.sendMessage("§7현재 이름: §e" + currentName);
            return true;
        }

        String newName = args[0];
        String currentName = dm.getRegisteredName(player.getUniqueId());

        // 동일한 이름 확인
        if (currentName != null && currentName.equalsIgnoreCase(newName)) {
            player.sendMessage(plugin.getMsg("rename-same"));
            return true;
        }

        // 이름 유효성 검사
        RegisterCommand registerCmd = new RegisterCommand(plugin);
        String validationError = registerCmd.validateName(newName, player, currentName);
        if (validationError != null) {
            player.sendMessage(validationError);
            return true;
        }

        // 이름 중복 확인 (본인 이름 제외)
        if (dm.isNameTakenByOther(player.getUniqueId(), newName)) {
            player.sendMessage(plugin.getMsg("register-name-taken"));
            return true;
        }

        // 이름 변경 처리
        boolean success = dm.changeName(player.getUniqueId(), newName);
        if (!success) {
            player.sendMessage(plugin.getMsg("rename-not-registered"));
            return true;
        }

        // 이름 적용
        plugin.applyPlayerName(player);

        // 성공 메시지
        String successMsg = plugin.getMsg("rename-success").replace("%name%", newName);
        player.sendMessage(successMsg);
        player.sendMessage("§7이전 이름: §c" + currentName + " §7→ 새 이름: §a" + newName);

        // 성공 타이틀
        player.sendTitle("§a§l이름 변경 완료!", "§e" + currentName + " §7→ §a" + newName, 10, 60, 10);

        // 로그
        plugin.getLogger().info("[이름변경] " + player.getName()
                + " | " + currentName + " -> " + newName);

        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command,
                                      String alias, String[] args) {
        if (args.length == 1 && sender instanceof Player player) {
            String current = plugin.getDataManager().getRegisteredName(player.getUniqueId());
            if (current != null) {
                return Collections.singletonList(current);
            }
            return Collections.singletonList("<새이름>");
        }
        return Collections.emptyList();
    }
}
