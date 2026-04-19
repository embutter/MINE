package kr.plugin.register.managers;

import kr.plugin.register.RegisterPlugin;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

/**
 * TAB 플러그인 (by NEZNAMY) 과의 연동을 관리합니다.
 * TAB 플러그인이 없을 경우 Vanilla 방식(setPlayerListName)으로 폴백합니다.
 */
public class TabManager {

    private final RegisterPlugin plugin;
    private boolean tabEnabled = false;
    private Object tabApi = null; // me.neznamy.tab.api.TabAPI (리플렉션으로 접근)

    public TabManager(RegisterPlugin plugin) {
        this.plugin = plugin;
        checkTabPlugin();
    }

    private void checkTabPlugin() {
        Plugin tabPlugin = Bukkit.getPluginManager().getPlugin("TAB");
        if (tabPlugin != null && tabPlugin.isEnabled()) {
            try {
                // TAB API 존재 여부 확인
                Class.forName("me.neznamy.tab.api.TabAPI");
                tabEnabled = plugin.getConfig().getBoolean("tab-plugin.enabled", true);
                if (tabEnabled) {
                    plugin.getLogger().info("TAB 플러그인 감지 완료 - TAB 연동이 활성화됩니다.");
                }
            } catch (ClassNotFoundException e) {
                plugin.getLogger().warning("TAB 플러그인이 감지되었지만 API를 찾을 수 없습니다. 기본 탭 목록 방식을 사용합니다.");
                tabEnabled = false;
            }
        } else {
            plugin.getLogger().info("TAB 플러그인이 없습니다. Vanilla 탭 목록 방식을 사용합니다.");
            tabEnabled = false;
        }
    }

    /**
     * 등록된 플레이어의 TAB 이름을 업데이트합니다.
     */
    public void updateTabName(Player player, String registeredName) {
        String format = plugin.getConfig().getString("tab-plugin.tab-name-format", "§a%name%")
                .replace("%name%", registeredName)
                .replace("%player%", player.getName());

        if (tabEnabled) {
            updateTabPluginName(player, format);
        }
        // Vanilla 폴백: setPlayerListName 은 RegisterPlugin#applyPlayerName 에서 이미 설정됨
    }

    /**
     * 미등록 플레이어의 TAB 이름을 업데이트합니다.
     */
    public void updateUnregisteredName(Player player) {
        String format = plugin.getConfig().getString("tab-plugin.unregistered-format",
                "§c[미등록] §7%player%")
                .replace("%player%", player.getName());

        if (tabEnabled) {
            updateTabPluginName(player, format);
        }
    }

    /**
     * TAB API 를 통해 플레이어 이름을 설정합니다 (리플렉션 사용).
     * TAB 플러그인 버전에 따라 API 가 달라지므로 리플렉션으로 안전하게 접근합니다.
     */
    private void updateTabPluginName(Player player, String displayName) {
        try {
            // me.neznamy.tab.api.TabAPI.getInstance().getPlayer(uuid)
            Class<?> tabApiClass = Class.forName("me.neznamy.tab.api.TabAPI");
            Object instance = tabApiClass.getMethod("getInstance").invoke(null);

            Object tabPlayer = tabApiClass.getMethod("getPlayer", java.util.UUID.class)
                    .invoke(instance, player.getUniqueId());

            if (tabPlayer != null) {
                // tabPlayer.setCustomName(displayName)
                tabPlayer.getClass().getMethod("setCustomName", String.class)
                        .invoke(tabPlayer, displayName);
            }
        } catch (Exception e) {
            // TAB API 호출 실패 시 조용히 무시 (Vanilla 방식으로 폴백됨)
            if (plugin.getConfig().getBoolean("debug", false)) {
                plugin.getLogger().warning("TAB API 이름 설정 실패 (" + player.getName() + "): " + e.getMessage());
            }
        }
    }

    public boolean isTabEnabled() {
        return tabEnabled;
    }
}
