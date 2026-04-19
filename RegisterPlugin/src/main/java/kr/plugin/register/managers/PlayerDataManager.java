package kr.plugin.register.managers;

import kr.plugin.register.RegisterPlugin;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.*;

/**
 * 플레이어 회원가입 데이터를 관리합니다.
 * 데이터는 plugins/RegisterPlugin/playerdata.yml 에 저장됩니다.
 */
public class PlayerDataManager {

    private final RegisterPlugin plugin;
    private final File dataFile;
    private FileConfiguration dataConfig;

    // 인메모리 캐시
    // UUID -> 등록된 이름
    private final Map<UUID, String> registeredPlayers = new HashMap<>();
    // 사용 중인 이름 셋 (중복 체크)
    private final Set<String> usedNames = new HashSet<>();

    public PlayerDataManager(RegisterPlugin plugin) {
        this.plugin = plugin;
        dataFile = new File(plugin.getDataFolder(), "playerdata.yml");
        loadData();
    }

    // ────────────────────────────────────────────────
    //  파일 로드 / 저장
    // ────────────────────────────────────────────────

    private void loadData() {
        if (!dataFile.exists()) {
            try {
                plugin.getDataFolder().mkdirs();
                dataFile.createNewFile();
            } catch (IOException e) {
                plugin.getLogger().severe("playerdata.yml 생성 실패: " + e.getMessage());
            }
        }

        dataConfig = YamlConfiguration.loadConfiguration(dataFile);

        // players 섹션에서 데이터 로드
        if (dataConfig.isConfigurationSection("players")) {
            for (String uuidStr : dataConfig.getConfigurationSection("players").getKeys(false)) {
                try {
                    UUID uuid = UUID.fromString(uuidStr);
                    String name = dataConfig.getString("players." + uuidStr + ".name");
                    if (name != null && !name.isEmpty()) {
                        registeredPlayers.put(uuid, name);
                        usedNames.add(name.toLowerCase(Locale.ROOT));
                    }
                } catch (IllegalArgumentException e) {
                    plugin.getLogger().warning("잘못된 UUID: " + uuidStr);
                }
            }
        }

        plugin.getLogger().info("플레이어 데이터 로드 완료: " + registeredPlayers.size() + "명");
    }

    public void saveAll() {
        for (Map.Entry<UUID, String> entry : registeredPlayers.entrySet()) {
            dataConfig.set("players." + entry.getKey() + ".name", entry.getValue());
        }
        try {
            dataConfig.save(dataFile);
        } catch (IOException e) {
            plugin.getLogger().severe("playerdata.yml 저장 실패: " + e.getMessage());
        }
    }

    private void savePlayer(UUID uuid, String name) {
        dataConfig.set("players." + uuid + ".name", name);
        try {
            dataConfig.save(dataFile);
        } catch (IOException e) {
            plugin.getLogger().severe("플레이어 데이터 저장 실패 (" + uuid + "): " + e.getMessage());
        }
    }

    // ────────────────────────────────────────────────
    //  회원가입 / 이름 변경
    // ────────────────────────────────────────────────

    /**
     * 플레이어를 등록합니다.
     * @return true = 성공, false = 이미 등록됨
     */
    public boolean registerPlayer(UUID uuid, String name) {
        if (registeredPlayers.containsKey(uuid)) return false;

        registeredPlayers.put(uuid, name);
        usedNames.add(name.toLowerCase(Locale.ROOT));
        savePlayer(uuid, name);
        return true;
    }

    /**
     * 등록된 플레이어의 이름을 변경합니다.
     * @return true = 성공, false = 미등록 플레이어
     */
    public boolean changeName(UUID uuid, String newName) {
        if (!registeredPlayers.containsKey(uuid)) return false;

        // 기존 이름 해제
        String oldName = registeredPlayers.get(uuid);
        usedNames.remove(oldName.toLowerCase(Locale.ROOT));

        // 새 이름 등록
        registeredPlayers.put(uuid, newName);
        usedNames.add(newName.toLowerCase(Locale.ROOT));
        savePlayer(uuid, newName);
        return true;
    }

    // ────────────────────────────────────────────────
    //  조회
    // ────────────────────────────────────────────────

    public boolean isRegistered(UUID uuid) {
        return registeredPlayers.containsKey(uuid);
    }

    public String getRegisteredName(UUID uuid) {
        return registeredPlayers.get(uuid);
    }

    /**
     * 이름이 이미 사용 중인지 확인 (대소문자 구분 없음)
     */
    public boolean isNameTaken(String name) {
        return usedNames.contains(name.toLowerCase(Locale.ROOT));
    }

    /**
     * 특정 UUID 가 현재 사용 중인 이름과 동일한지 확인 (자기 자신 제외)
     */
    public boolean isNameTakenByOther(UUID uuid, String name) {
        String current = registeredPlayers.get(uuid);
        if (current != null && current.equalsIgnoreCase(name)) return false; // 본인 이름
        return usedNames.contains(name.toLowerCase(Locale.ROOT));
    }

    public Map<UUID, String> getAllRegistered() {
        return Collections.unmodifiableMap(registeredPlayers);
    }
}
