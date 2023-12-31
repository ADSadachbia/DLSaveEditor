import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.*;

import com.google.gson.*;
import com.google.gson.stream.JsonReader;
import meta.*;

public class JsonUtils {

    private static final int MAX_DRAGON_CAPACITY = 525;

    private static Options options;
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private final String savePath;
    private String optionsPath;
    private String jarPath;

    private String basePath = "";
    private final String rsrcPath;
    private boolean toOverwrite = false;
    private boolean inJar;

    private Random rng = new Random();

    //savefile
    private JsonObject jsonData;

    //pulled from datamine
    private JsonArray abilitiesList;

    //Ability Name --> Ability ID
    private HashMap<String, Integer> kscapeAbilityMap = new HashMap<>();
    //Adventurer Title --> Portrait Print ID
    private HashMap<String, Integer> kscapeLabelsMap = new HashMap<>();
    private List<Integer> kscapePortraitIDs = new ArrayList<>();
    //Adventurer ID --> Adventurer Story IDs
    private HashMap<Integer, List<Integer>> adventurerStoryMap = new HashMap<>();

    //Maps
    private HashMap<Integer, AdventurerMeta> idToAdventurer = new HashMap<>();
    private HashMap<String, AdventurerMeta> nameToAdventurer = new HashMap<>();

    private HashMap<Integer, DragonMeta> idToDragon = new HashMap<>();
    private HashMap<String, DragonMeta> nameToDragon = new HashMap<>();

    private HashMap<Integer, WeaponMeta> idToWeapon = new HashMap<>();
    private HashMap<Integer, String> idToWeaponSkinName = new HashMap<>();
    private HashMap<Integer, WyrmprintMeta> idToPrint = new HashMap<>();
    private HashMap<Integer, FacilityMeta> idToFacility = new HashMap<>();
    private HashMap<Integer, MaterialMeta> idToMaterial = new HashMap<>();

    //Alias Maps
    private HashMap<String, List<String>> adventurerAliases = new HashMap<>();
    private HashMap<String, List<String>> dragonAliases = new HashMap<>();


    private JsonObject maxedFacilityBonuses;

    private final List<String> testFlags = new ArrayList<>();

    public JsonUtils(String savePath, String optionsPath, String jarPath, boolean inJar) {
        log("Initializing JsonUtils...");

        this.savePath = savePath;
        this.optionsPath = optionsPath;
        this.jarPath = jarPath;
        this.inJar = inJar;
        rsrcPath = Paths.get(jarPath, "rsrc").toString();
        try {
            this.jsonData = getSaveData().getAsJsonObject();
            readAliasesData();
            readAdventurerData();
            readDragonsData();
            readKscapeData();
            readKscapeLabels();
            readStoryData();
            readWeaponSkinData();
            readWeaponsData();
            readPrintsData();
            readAbilitiesData();
            readFacilitiesData();
            readMaterialsData();
            readOptionsData();
        } catch (IOException e) {
            e.printStackTrace();
            System.out.println("Unable to read JSON data!");
            System.exit(99);
        }

    }

    public void writeToFile() {
        try {
            String newPath;
            String fileName = "savedata2.txt";
            if (isSaveData2Present() && !toOverwrite) {
                int count = 3;
                fileName = "savedata" + count + ".txt";
                while (new File(Paths.get(basePath, fileName).toString()).exists()) {
                    count++;
                    fileName = "savedata" + count + ".txt";
                }
                newPath = Paths.get(basePath, fileName).toString();
            } else {
                newPath = Paths.get(basePath, fileName).toString();
            }
            FileWriter fileWriter = new FileWriter(newPath);
            GSON.toJson(jsonData, fileWriter);
            fileWriter.flush();
            fileWriter.close();
            if(inJar) {
                System.out.println("Saved output JSON to " + Paths.get(jarPath, fileName));
            } else {
                System.out.println("Saved output JSON to " + newPath);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public boolean checkTests(){
        Tests tests = new Tests(this);
        log("noDupeCharaIdTest(): " + tests.noDupeCharaIdTest());
        log("noDupeDragonKeyIdTest(): " + tests.noDupeDragonKeyIdTest());
        log("noDupeTalismanKeyIdTest(): " + tests.noDupeTalismanKeyIdTest());
        log("noDupeWeaponSkinIdTest(): " + tests.noDupeWeaponSkinIdTest());
        log("noDupeCrestIdTest(): " + tests.noDupeCrestIdTest());
        log("weaponPassivesIdTest(): " + tests.weaponPassivesIdTest());
        log("weaponPassivesIdPerWeaponTest(): " + tests.weaponPassivesIdPerWeaponTest());
        if(!tests.getIfAllPassed()){
            System.out.println("One or more tests failed... cannot export savedata. " +
                    "Contact @sockperson if this message appears.");
            return false;
        }
        return true;
    }

    public boolean isSaveData2Present() {
        return new File(Paths.get(basePath, "savedata2.txt").toString()).exists();
    }

    public void setOverwrite(boolean toOverwrite) {
        this.toOverwrite = toOverwrite;
    }

    public double addDoubles(double val1, double val2) {
        //sure hope this prevents floating point inaccuracy
        return ((10.0 * val1) + (10.0 * val2)) / 10;
    }

    public JsonObject getJsonData() {
        return jsonData;
    }

    public List<String> getTestFlags() { return testFlags; }

    private JsonObject getSaveData() throws IOException {
        JsonReader reader = new JsonReader(new FileReader(savePath));
        return GSON.fromJson(reader, JsonObject.class);
    }

    private BufferedReader getBufferedReader(String... more){
        return new BufferedReader(getRsrcReader(more));
    }

    private InputStreamReader getRsrcReader(String... more){
        //getResourceAsStream() doesn't like backslashes i think...?
        //hope this doesn't break on other OS...
        String path = (File.separator + Paths.get("rsrc", more)).replace("\\", "/");
        InputStream in;
        in = JsonUtils.class.getResourceAsStream(path);
        if(in == null){
            System.out.println(path);
            in = JsonUtils.class.getClassLoader().getResourceAsStream(path);
        }
        if(in == null){
            System.out.println("Could not load resource!");
            System.exit(92);
        }
        return new InputStreamReader(in, StandardCharsets.UTF_8);
    }

    private JsonArray getJsonArray(String more) {
        JsonReader reader = new JsonReader(getRsrcReader(more));
        return GSON.fromJson(reader, JsonArray.class);
    }

    private JsonObject getJsonObject(String more) {
        JsonReader reader = new JsonReader(getRsrcReader(more));
        return GSON.fromJson(reader, JsonObject.class);
    }

    public String getFieldAsString(String... memberNames) {
        return getField(memberNames).getAsString();
    }

    public int getFieldAsInt(String... memberNames) {
        return getField(memberNames).getAsInt();
    }

    public JsonArray getFieldAsJsonArray(String... memberNames) {
        return getField(memberNames).getAsJsonArray();
    }

    public JsonObject getFieldAsJsonObject(String... memberNames) {
        return getField(memberNames).getAsJsonObject();
    }

    public List<Integer> jsonArrayToList(JsonArray value){
        ArrayList<Integer> out = new ArrayList<>();
        for (JsonElement jsonEle : value) {
            out.add(jsonEle.getAsInt());
        }
        return out;
    }

    private void writeInteger(int value, String... memberNames) {
        List<String> memberNameList = new ArrayList<>(Arrays.asList(memberNames));

        JsonElement jsonEle = jsonData;
        for (int i = 0; i < memberNameList.size() - 1; i++) {
            jsonEle = jsonEle.getAsJsonObject().get(memberNameList.get(i));
        }
        String lastMemberName = memberNameList.get(memberNameList.size() - 1);
        JsonObject jsonObj = jsonEle.getAsJsonObject();
        jsonObj.remove(lastMemberName);
        jsonObj.addProperty(lastMemberName, value);
    }

    private void writeLong(long value, String... memberNames) {
        List<String> memberNameList = new ArrayList<>(Arrays.asList(memberNames));

        JsonElement jsonEle = jsonData;
        for (int i = 0; i < memberNameList.size() - 1; i++) {
            jsonEle = jsonEle.getAsJsonObject().get(memberNameList.get(i));
        }
        String lastMemberName = memberNameList.get(memberNameList.size() - 1);
        JsonObject jsonObj = jsonEle.getAsJsonObject();
        jsonObj.remove(lastMemberName);
        jsonObj.addProperty(lastMemberName, value);
    }

    private JsonElement getField(String... memberNames) {
        JsonElement jsonEle = jsonData;
        for (String memberName : memberNames) {
            if (jsonEle.isJsonObject()) {
                jsonEle = jsonEle.getAsJsonObject().get(memberName);
            }
        }
        return jsonEle;
    }

    //List Utils
    private int getSum(JsonObject src, String... memberNames) {
        int sum = 0;
        for (String memberName : memberNames) {
            sum += src.get(memberName).getAsInt();
        }
        return sum;
    }

    public Set<Integer> getSetFromField(String fieldName, String... memberNames){
        Set<Integer> out = new HashSet<>();
        getFieldAsJsonArray(memberNames).forEach(jsonEle ->
                out.add(jsonEle.getAsJsonObject().get(fieldName).getAsInt()));
        return out;
    }

    //mainly used to get max keyId
    private int getMaxFromObjListField(String fieldName, String... memberNames){
        int max = -1;
        JsonArray jsonArray = getFieldAsJsonArray(memberNames);
        for (JsonElement jsonEle : jsonArray) {
            JsonObject jsonObj = jsonEle.getAsJsonObject();
            max = Math.max(max, jsonObj.get(fieldName).getAsInt());
        }
        return max;
    }

    //Reads
    private void readAliasesData() throws IOException {
        //Get aliases
        BufferedReader br = getBufferedReader("adventurerAliases.txt");
        String out = br.readLine();
        while (out != null) {
            String[] split = out.split(",");
            String name = split[0].toUpperCase();
            List<String> advAliases = new ArrayList<>();
            for(int i = 1; i < split.length; i++){
                advAliases.add(split[i].toUpperCase());
            }
            adventurerAliases.put(name, advAliases);
            out = br.readLine();
        }
        br = getBufferedReader("dragonAliases.txt");
        out = br.readLine();
        while (out != null) {
            String[] split = out.split(",");
            String name = split[0].toUpperCase();
            List<String> drgAliases = new ArrayList<>();
            for(int i = 1; i < split.length; i++){
                drgAliases.add(split[i].toUpperCase());
            }
            dragonAliases.put(name, drgAliases);
            out = br.readLine();
        }
    }

    private void readAdventurerData() throws IOException {
        for (JsonElement jsonEle : getJsonArray("adventurers.json")) {
            JsonObject adv = jsonEle.getAsJsonObject();
            String baseName = adv.get("FullName").getAsString();
            String name = baseName.toUpperCase();
            if (name.equals("PUPPY")) {
                continue; //dog check //...i should just remove this
            }
            //fill idToAdventurer map
            boolean hasManaSpiral = !(adv.get("ManaSpiralDate") instanceof JsonNull);
            int hp, str;
            int id = adv.get("IdLong").getAsInt();
            if (hasManaSpiral) {
                hp = getSum(adv, "AddMaxHp1", "PlusHp0", "PlusHp1", "PlusHp2", "PlusHp3", "PlusHp4", "PlusHp5", "McFullBonusHp5");
                str = getSum(adv, "AddMaxAtk1", "PlusAtk0", "PlusAtk1", "PlusAtk2", "PlusAtk3", "PlusAtk4", "PlusAtk5", "McFullBonusAtk5");
            } else {
                hp = getSum(adv, "MaxHp", "PlusHp0", "PlusHp1", "PlusHp2", "PlusHp3", "PlusHp4", "McFullBonusHp5");
                str = getSum(adv, "MaxAtk", "PlusAtk0", "PlusAtk1", "PlusAtk2", "PlusAtk3", "PlusAtk4", "McFullBonusAtk5");
            }
            int maxA3Level = 1;
            if(adv.get("Abilities32").getAsInt() != 0){
                maxA3Level = 2;
                if(adv.get("Abilities33").getAsInt() != 0){
                    maxA3Level = 3;
                }
            }
            String manaCircleType = adv.get("ManaCircleName").getAsString();
            AdventurerMeta unit = new AdventurerMeta(baseName, adv.get("Title").getAsString(), id,
                    adv.get("ElementalTypeId").getAsInt(), hp, str,adv.get("MaxLimitBreakCount").getAsInt(),
                    adv.get("EditSkillCost").getAsInt() != 0, hasManaSpiral, maxA3Level,
                    adv.get("MinHp3").getAsInt(), adv.get("MinHp4").getAsInt(), adv.get("MinHp5").getAsInt(),
                    adv.get("MinAtk3").getAsInt(), adv.get("MinAtk4").getAsInt(), adv.get("MinAtk5").getAsInt(),
                    adv.get("Rarity").getAsInt(), manaCircleType
                    );
            idToAdventurer.put(id, unit);
            nameToAdventurer.put(name, unit);
            if(adventurerAliases.containsKey(name)){
                adventurerAliases.get(name).forEach(alias -> nameToAdventurer.put(alias.toUpperCase(), unit));
            }
        }
    }

    private void readDragonsData() throws IOException {
        for (JsonElement jsonEle : getJsonArray("dragons.json")) {
            JsonObject drg = jsonEle.getAsJsonObject();
            String baseName = drg.get("FullName").getAsString();
            String name = baseName.toUpperCase();
            if (drg.get("IsPlayable").getAsInt() == 0) {
                continue;
            }
            //fill idToDragon
            boolean has5UB = drg.get("MaxLimitBreakCount").getAsInt() == 5;
            int id = drg.get("Id").getAsInt();
            int rarity = drg.get("Rarity").getAsInt();
            int a1Level = has5UB ?
                    6 : drg.get("Abilities15").getAsInt() != 0 ?
                    5 : 0;
            int a2Level = has5UB ?
                    6 : drg.get("Abilities25").getAsInt() != 0 ?
                    5 : 0;
            boolean hasA2 = drg.get("Abilities21").getAsInt() != 0;
            DragonMeta unit = new DragonMeta(baseName, id, drg.get("ElementalTypeId").getAsInt(),
                a1Level, a2Level, rarity, has5UB, hasA2);
            idToDragon.put(id, unit);
            nameToDragon.put(name, unit);
            if(dragonAliases.containsKey(name)){
                dragonAliases.get(name).forEach(alias -> nameToDragon.put(alias.toUpperCase(), unit));
            }
        }
    }

    private void readAbilitiesData() throws IOException {
        abilitiesList = getJsonArray("abilities.json");
    }

    private void readMaterialsData() throws IOException {
        getJsonArray("materials.json").forEach(jsonEle -> {
            JsonObject mat = jsonEle.getAsJsonObject();
            String name = mat.get("Name").getAsString();
            int id = mat.get("Id").getAsInt();
            String category;
            if(mat.has("Category")){
                category = mat.get("Category").getAsString();
            } else {
               category = "Idk";
            }
            idToMaterial.put(id, new MaterialMeta(name, id, category));
        });
    }

    private void readFacilitiesData() throws IOException {
        HashMap<Integer, JsonObject> facilitiesMap = new HashMap<>();
        //pull wiki facilities data
        getJsonArray("facilities.json").forEach(jsonEle -> {
            JsonObject facility = jsonEle.getAsJsonObject();
            int id = facility.get("Id").getAsInt();
            facilitiesMap.put(id, facility);
        });

        BufferedReader br = getBufferedReader("FortPlantDetail.txt");
        br.readLine(); //ignore first line
        String out = br.readLine();

        int id = 1337;
        int maxLevel = -1;
        //this code sucks balls who wrote it
        while (out != null) {
            String[] split = out.split(",");
            String longID = split[0]; //longID is facility ID AAAAAA + level BB

            int newId = Integer.parseInt(longID.substring(0,6));
            if(id != newId){ //starting to read thru data for next facility... assign facility max level to list
                if(id != 1337){
                    boolean isResourceFacility = id == 100101 || id == 100201 || id == 100301; //halidom, mine, dragontree
                    FacilityMeta fac = new FacilityMeta(facilitiesMap.get(id).get("Name").getAsString(), id, maxLevel,
                            isResourceFacility, facilitiesMap.get(id).get("Available").getAsInt());
                    idToFacility.put(id, fac);
                    maxLevel = -1;
                }
                id = newId;
            }
            maxLevel = Math.max(Integer.parseInt(split[3]), maxLevel);
            out = br.readLine();
        }

        //get max facility bonus data
        maxedFacilityBonuses = getJsonObject("maxedFacilityBonuses.json");
    }

    private void readWeaponsData() throws IOException {
        for(JsonElement jsonEle : getJsonArray("weapons.json")){
            JsonObject weaponData = jsonEle.getAsJsonObject();
            int id = weaponData.get("Id").getAsInt();
            List<Integer> passiveIds = jsonArrayToList(weaponData.get("PassiveAbilities").getAsJsonArray());
            WeaponMeta weapon = new WeaponMeta(weaponData.get("Name").getAsString(), id,
                    weaponData.get("ElementalTypeId").getAsInt(), weaponData.get("WeaponTypeId").getAsInt(),
                    weaponData.get("WeaponSeries").getAsString(), weaponData.get("Rarity").getAsInt(),
                    passiveIds, weaponData.get("HasWeaponBonus").getAsBoolean());
            idToWeapon.put(id, weapon);
        }
    }

    private void readPrintsData() throws IOException {
        for(JsonElement jsonEle : getJsonArray("prints.json")){
            JsonObject printData = jsonEle.getAsJsonObject();
            int id = printData.get("Id").getAsInt();
            WyrmprintMeta print = new WyrmprintMeta(printData.get("Name").getAsString(),
                    id, printData.get("Rarity").getAsInt());
            idToPrint.put(id, print);
        }
    }

    private void readKscapeData() throws IOException {
        JsonObject kscapeJson = getJsonObject("kscape.json");
        for (Map.Entry<String, JsonElement> entry : kscapeJson.entrySet()) {
            kscapeAbilityMap.put(entry.getKey(), entry.getValue().getAsInt());
        }
    }

    private void readKscapeLabels() throws IOException {
        BufferedReader br = getBufferedReader("kscapeLabels.txt");
        String out = br.readLine();
        while (out != null) {
            String[] split1 = out.split("\t");
            int id = Integer.parseInt(split1[0].split("_")[2]);
            String label = split1[2];
            kscapeLabelsMap.put(label, id);
            kscapePortraitIDs.add(id);
            out = br.readLine();
        }
    }

    private void readOptionsData() throws IOException {
        options = new Options(optionsPath);
    }

    private void readStoryData() throws IOException {
        for(Map.Entry<String, JsonElement> entry : getJsonObject("CharaStories.json").entrySet()){
            int id = Integer.parseInt(entry.getKey());
            JsonObject stories = entry.getValue().getAsJsonObject();
            List<Integer> storyIDs = new ArrayList<>();
            storyIDs.add(stories.get("0").getAsInt());
            storyIDs.add(stories.get("1").getAsInt());
            storyIDs.add(stories.get("2").getAsInt());
            storyIDs.add(stories.get("3").getAsInt());
            storyIDs.add(stories.get("4").getAsInt());
            adventurerStoryMap.put(id, storyIDs);
        }
    }

    private void readWeaponSkinData() throws IOException {
        BufferedReader br = getBufferedReader("weaponSkins.txt");
        String out = br.readLine();
        while (out != null) {
            String[] fields = out.split("\t");
            int skinID = Integer.parseInt(fields[0].split("_")[3]);
            String name = fields[2];
            out = br.readLine();
            idToWeaponSkinName.put(skinID, name);
        }
    }

    private void addAdventurerEncyclopediaBonus(AdventurerMeta adv) {
        boolean hasManaSpiral = adv.hasManaSpiral();
        double bonus = hasManaSpiral ? 0.3 : 0.2;
        int elementID = adv.getElementId();
        if (options.getFieldValue("maxAddedAdventurers")) {
            addAdventurerEncyclopediaBonus(elementID, bonus, bonus);
        } else { //bonus from adding new adventurer (no upgrades)
            addAdventurerEncyclopediaBonus(elementID, 0.1, 0.1);
        }
    }

    private void addAdventurerEncyclopediaBonus(int elementId, double hpBonus, double strBonus) {
        JsonArray albumBonuses = getFieldAsJsonArray("data", "fort_bonus_list", "chara_bonus_by_album");
        for (JsonElement jsonEle : albumBonuses) {
            JsonObject albumBonus = jsonEle.getAsJsonObject();
            if (albumBonus.get("elemental_type").getAsInt() == elementId) {
                double hp = albumBonus.get("hp").getAsDouble();
                double attack = albumBonus.get("attack").getAsDouble();
                double resultHp = addDoubles(hp, hpBonus);
                double resultStr = addDoubles(attack, strBonus);
                albumBonus.remove("hp");
                albumBonus.remove("attack");
                albumBonus.addProperty("hp", resultHp);
                albumBonus.addProperty("attack", resultStr);
            }
        }
    }

    private void addDragonEncyclopediaBonus(DragonMeta dragon) {
        boolean has5UB = dragon.has5UB();
        double hpBonus = has5UB ? 0.3 : 0.2;
        double strBonus = 0.1;
        int elementID = dragon.getElementId();
        if (options.getFieldValue("maxAddedDragons")) {
            addDragonEncyclopediaBonus(elementID, hpBonus, strBonus);
        } else {
            addDragonEncyclopediaBonus(elementID, 0.1, 0.1);
        }
    }

    private void addDragonEncyclopediaBonus(int elementID, double hpBonus, double strBonus) {
        JsonArray albumBonuses = getFieldAsJsonArray("data", "fort_bonus_list", "dragon_bonus_by_album");
        for (JsonElement jsonEle : albumBonuses) {
            JsonObject albumBonus = jsonEle.getAsJsonObject();
            if (albumBonus.get("elemental_type").getAsInt() == elementID) {
                double hp = albumBonus.get("hp").getAsDouble();
                double attack = albumBonus.get("attack").getAsDouble();
                double resultHp = addDoubles(hp, hpBonus);
                double resultStr = addDoubles(attack, strBonus);
                albumBonus.remove("hp");
                albumBonus.remove("attack");
                albumBonus.addProperty("hp", resultHp);
                albumBonus.addProperty("attack", resultStr);
            }
        }
    }

    private void addWeaponBonus(WeaponMeta weapon) {
        if(!weapon.hasWeaponBonus()) {
            return;
        }
        String weaponSeries = weapon.getWeaponSeries();
        double bonus = 0.0;
        switch (weaponSeries) {
            case "Core":
            case "Void":
            case "Chimeratech":
                bonus = 0.5;
                break;
            case "High Dragon":
            case "Agito":
            case "Primal Dragon":
                bonus = 1.5;
                break;
        }
        if (bonus == 0.0) {
            return; //no bonus added
        }
        int weaponTypeId = weapon.getWeaponTypeId();
        JsonArray weaponBonuses = getFieldAsJsonArray("data", "fort_bonus_list", "param_bonus_by_weapon");
        for (JsonElement jsonEle : weaponBonuses) {
            JsonObject weaponBonus = jsonEle.getAsJsonObject();
            if (weaponBonus.get("weapon_type").getAsInt() == weaponTypeId) {
                //for weapon bonuses: hp will always be equal to str
                double value = weaponBonus.get("hp").getAsDouble();
                double resultBonus = addDoubles(value, bonus);
                weaponBonus.remove("hp");
                weaponBonus.remove("attack");
                weaponBonus.addProperty("hp", resultBonus);
                weaponBonus.addProperty("attack", resultBonus);
            }
        }
    }

    //Builders

    private void addTalisman(String advName, int id1, int id2, int id3, int count) {
        for(int i = 0; i < count; i++){
            JsonObject out = new JsonObject();

            int keyIdMax = 0;   //need to keep track of keyId
            //Obtain keyIdMax
            JsonArray ownedTalismans = getFieldAsJsonArray("data", "talisman_list");
            for (JsonElement jsonEle : ownedTalismans) {
                JsonObject ownedTalisman = jsonEle.getAsJsonObject();
                keyIdMax = Math.max(keyIdMax, ownedTalisman.get("talisman_key_id").getAsInt());
            }
            String name = advName.toUpperCase();
            if(!nameToAdventurer.containsKey(name)){
                System.out.println("No adventurer found for name: " + advName + "!");
                return;
            }
            String label = nameToAdventurer.get(name).getTitle();
            if(!kscapeLabelsMap.containsKey(label)){
                System.out.println("No ID found for label:" + label + "!");
            }
            int portraitID = kscapeLabelsMap.get(label);

            out.addProperty("talisman_key_id", keyIdMax + 200);
            out.addProperty("talisman_id", portraitID);
            out.addProperty("is_lock", 0);
            out.addProperty("is_new", 1);
            out.addProperty("talisman_ability_id_1", id1);
            out.addProperty("talisman_ability_id_2", id2);
            out.addProperty("talisman_ability_id_3", id3);
            out.addProperty("additional_hp", 100);
            out.addProperty("additional_attack", 100);
            out.addProperty("gettime", Instant.now().getEpochSecond());

            getFieldAsJsonArray("data", "talisman_list").add(out);
        }
    }

    private JsonObject buildTalisman(String label, String[] combo, int keyIdOffset) {
        JsonObject out = new JsonObject();
        int abilityId1 = 0;
        int abilityId2 = 0;
        int abilityId3 = 0;
        for (int i = 0; i < combo.length; i++) {
            int id = kscapeAbilityMap.get(combo[i]);
            switch (i) {
                case 0:
                    abilityId1 = id;
                    break;
                case 1:
                    abilityId2 = id;
                    break;
                case 2:
                    abilityId3 = id;
                    break;
            }
        }

        out.addProperty("talisman_key_id", 200000 + 100 * keyIdOffset);
        out.addProperty("talisman_id", kscapeLabelsMap.get(label));
        out.addProperty("is_lock", 0);
        out.addProperty("is_new", 1);
        out.addProperty("talisman_ability_id_1", abilityId1);
        out.addProperty("talisman_ability_id_2", abilityId2);
        out.addProperty("talisman_ability_id_3", abilityId3);
        out.addProperty("additional_hp", 100);
        out.addProperty("additional_attack", 100);
        out.addProperty("gettime", Instant.now().getEpochSecond());

        return out;
    }

    private JsonObject buildRandomTalisman(int id, int keyIdOffset) {
        JsonObject out = new JsonObject();
        int totalAbilitiesCount = abilitiesList.size();

        out.addProperty("talisman_key_id", 200000 + 100 * keyIdOffset);
        out.addProperty("talisman_id", id);
        out.addProperty("is_lock", 0);
        out.addProperty("is_new", 1);
        out.addProperty("talisman_ability_id_1", abilitiesList.get(rng.nextInt(totalAbilitiesCount)).getAsJsonObject().get("Id").getAsInt());
        out.addProperty("talisman_ability_id_2", abilitiesList.get(rng.nextInt(totalAbilitiesCount)).getAsJsonObject().get("Id").getAsInt());
        out.addProperty("talisman_ability_id_3", abilitiesList.get(rng.nextInt(totalAbilitiesCount)).getAsJsonObject().get("Id").getAsInt());
        out.addProperty("additional_hp", 100);
        out.addProperty("additional_attack", 100);
        out.addProperty("gettime", Instant.now().getEpochSecond());

        return out;
    }

    //Returns a built adventurer in savedata.txt format
    private JsonObject buildUnit(AdventurerMeta adventurerData, int getTime) {
        JsonObject out = new JsonObject();
        if (adventurerData.getName().equals("Puppy")) {
            return null; //no dogs allowed
        }

        //to add new unit as a level 1 un-upgraded unit
        boolean minUnit = getTime == -1 && !options.getFieldValue("maxAddedAdventurers");

        boolean hasManaSpiral = adventurerData.hasManaSpiral();
        JsonArray mc = new JsonArray();
        int mcLevel = hasManaSpiral ? 70 : 50;
        for (int i = 1; i <= mcLevel; i++) {
            mc.add(i);
        }
        if (!minUnit) { //maxed unit
            out.addProperty("chara_id", adventurerData.getId());
            out.addProperty("rarity", 5);
            out.addProperty("exp", hasManaSpiral ? 8866950 : 1191950);
            out.addProperty("level", hasManaSpiral ? 100 : 80);
            out.addProperty("additional_max_level", hasManaSpiral ? 20 : 0);
            out.addProperty("hp_plus_count", 100);
            out.addProperty("attack_plus_count", 100);
            out.addProperty("limit_break_count", adventurerData.getMaxLimitBreakCount());
            out.addProperty("is_new", 1);
            out.addProperty("gettime", getTime == -1 ? Instant.now().getEpochSecond() : getTime);
            out.addProperty("skill_1_level", hasManaSpiral ? 4 : 3);
            out.addProperty("skill_2_level", hasManaSpiral ? 3 : 2);
            out.addProperty("ability_1_level", hasManaSpiral ? 3 : 2);
            out.addProperty("ability_2_level", hasManaSpiral ? 3 : 2);
            out.addProperty("ability_3_level", adventurerData.getMaxA3Level()); //this varies per adventurer
            out.addProperty("burst_attack_level", 2);
            out.addProperty("combo_buildup_count", hasManaSpiral ? 1 : 0);
            out.addProperty("hp", adventurerData.getMaxHp());
            out.addProperty("attack", adventurerData.getMaxStr());
            out.addProperty("ex_ability_level", 5);
            out.addProperty("ex_ability_2_level", 5);
            out.addProperty("is_temporary", 0);
            out.addProperty("is_unlock_edit_skill", adventurerData.hasSkillShare() ? 1 : 0);
            out.add("mana_circle_piece_id_list", mc);
            out.addProperty("list_view_flag", 1);
        } else { //un-upgraded unit
            out.addProperty("chara_id", adventurerData.getId());
            out.addProperty("rarity", adventurerData.getBaseRarity());
            out.addProperty("exp", 0);
            out.addProperty("level", 1);
            out.addProperty("additional_max_level", 0);
            out.addProperty("hp_plus_count", 0);
            out.addProperty("attack_plus_count", 0);
            out.addProperty("limit_break_count", 0); //confirm?
            out.addProperty("is_new", 1);
            out.addProperty("gettime", Instant.now().getEpochSecond());
            out.addProperty("skill_1_level", 1); //confirm?
            out.addProperty("skill_2_level", 0);
            out.addProperty("ability_1_level", adventurerData.getMinA1Level());
            out.addProperty("ability_2_level", 0);
            out.addProperty("ability_3_level", 0);
            out.addProperty("burst_attack_level", adventurerData.getMinFsLevel());
            out.addProperty("combo_buildup_count", 0);
            out.addProperty("hp", adventurerData.getMinHp()); //get min
            out.addProperty("attack", adventurerData.getMinStr()); //get min
            out.addProperty("ex_ability_level", 1);
            out.addProperty("ex_ability_2_level", 1);
            out.addProperty("is_temporary", 0);
            out.addProperty("is_unlock_edit_skill", 0);
            out.add("mana_circle_piece_id_list", new JsonArray());
            out.addProperty("list_view_flag", 1);
        }
        return out;
    }

    //Returns a built dragon in savedata.txt format
    private JsonObject buildDragon(DragonMeta dragonData, int keyIdMin, int keyIdOffset) {
        JsonObject out = new JsonObject();
        boolean has5UB = dragonData.has5UB();
        int xp = dragonData.getMaxXp();
        int level = dragonData.getMaxLevel();

        int a1Level = dragonData.getA1Max();
        int a2Level = dragonData.getA2Max();

        boolean minDragon = !options.getFieldValue("maxAddedDragons");
        if (!minDragon) {
            out.addProperty("dragon_key_id", keyIdMin + 200 * keyIdOffset);
            out.addProperty("dragon_id", dragonData.getId());
            out.addProperty("level", level);
            out.addProperty("hp_plus_count", 50);
            out.addProperty("attack_plus_count", 50);
            out.addProperty("exp", xp);
            out.addProperty("is_lock", 0);
            out.addProperty("is_new", 1);
            out.addProperty("get_time", Instant.now().getEpochSecond());
            out.addProperty("skill_1_level", 2);
            out.addProperty("ability_1_level", a1Level);
            out.addProperty("ability_2_level", a2Level);
            out.addProperty("limit_break_count", has5UB ? 5 : 4);
        } else {
            out.addProperty("dragon_key_id", keyIdMin + 200 * keyIdOffset);
            out.addProperty("dragon_id", dragonData.getId());
            out.addProperty("level", 1);
            out.addProperty("hp_plus_count", 0);
            out.addProperty("attack_plus_count", 0);
            out.addProperty("exp", 0);
            out.addProperty("is_lock", 0);
            out.addProperty("is_new", 1);
            out.addProperty("get_time", Instant.now().getEpochSecond());
            out.addProperty("skill_1_level", 1);
            out.addProperty("ability_1_level", 1);
            out.addProperty("ability_2_level", dragonData.hasA2() ? 1 : 0);
            out.addProperty("limit_break_count", 0);
        }
        return out;
    }

    //Returns a built dragon in savedata.txt format
    //Takes in getTime and keyId info and returns maxed out dragon
    //Used to upgrade currently owned dragons
    private JsonObject buildDragon2(DragonMeta dragonData, int keyId, int getTime) {
        JsonObject out = new JsonObject();
        boolean has5UB = dragonData.has5UB();
        int xp = dragonData.getMaxXp();
        int level = dragonData.getMaxLevel();

        int a1Level = dragonData.getA1Max();
        int a2Level = dragonData.getA2Max();

        out.addProperty("dragon_key_id", keyId);
        out.addProperty("dragon_id", dragonData.getId());
        out.addProperty("level", level);
        out.addProperty("hp_plus_count", 50);
        out.addProperty("attack_plus_count", 50);
        out.addProperty("exp", xp);
        out.addProperty("is_lock", 0);
        out.addProperty("is_new", 1);
        out.addProperty("get_time", getTime);
        out.addProperty("skill_1_level", 2);
        out.addProperty("ability_1_level", a1Level);
        out.addProperty("ability_2_level", a2Level);
        out.addProperty("limit_break_count", has5UB ? 5 : 4);
        return out;
    }

    private JsonObject buildDragonAlbumData(DragonMeta dragonData) {
        JsonObject out = new JsonObject();
        boolean has5UB = dragonData.has5UB();
        int level = dragonData.getMaxLevel();

        out.addProperty("dragon_id", dragonData.getId());
        out.addProperty("max_level", level);
        out.addProperty("max_limit_break_count", has5UB ? 5 : 4);
        return out;
    }

    //build facility from existing facility
    private JsonObject buildFacility(FacilityMeta fac, int keyId, int x, int y){
        JsonObject out = new JsonObject();

        boolean isResourceFacility = fac.isResourceFacility();
        int level = fac.getMaxLevel();
        int id = fac.getId();
        int detailId = Integer.parseInt(fac.getDetailId());

        out.addProperty("build_id", keyId);  //key ID
        out.addProperty("fort_plant_detail_id", detailId); //ID + level
        out.addProperty("position_x", x);
        out.addProperty("position_z", y);
        out.addProperty("build_status", 0);
        out.addProperty("build_start_date", 0);
        out.addProperty("build_end_date", 0);
        out.addProperty("level", level);
        out.addProperty("plant_id", id);  //id
        out.addProperty("is_new", 0);
        out.addProperty("remain_time", 0);
        out.addProperty("last_income_date", isResourceFacility ? (Instant.now().getEpochSecond()) : -1);  //resource facility
        if(isResourceFacility){
            out.addProperty("last_income_time", 200000);  //resource facility
        }
        return out;
    }

    //build a new facility
    private JsonObject buildFacility(FacilityMeta fac, int keyIdMin, int keyIdOffset){
        JsonObject out = new JsonObject();

        boolean isResourceFacility = fac.isResourceFacility();
        int level = fac.getMaxLevel();
        int id = fac.getId();
        int detailId = Integer.parseInt(fac.getDetailId());

        out.addProperty("build_id", keyIdMin + 200 * keyIdOffset);  //key ID
        out.addProperty("fort_plant_detail_id", detailId); //ID + level
        out.addProperty("position_x", -1); //xz = -1 for facilities in inventory
        out.addProperty("position_z", -1);
        out.addProperty("build_status", 0);
        out.addProperty("build_start_date", 0);
        out.addProperty("build_end_date", 0);
        out.addProperty("level", level);
        out.addProperty("plant_id", id);  //id
        out.addProperty("is_new", 0);
        out.addProperty("remain_time", 0);
        out.addProperty("last_income_date", isResourceFacility ? (Instant.now().getEpochSecond()) : -1);  //resource facility
        if(isResourceFacility){
            out.addProperty("last_income_time", 200000);  //resource facility
        }
        return out;
    }

    private JsonObject buildWeapon(WeaponMeta weaponData, int getTime) {
        JsonObject out = new JsonObject();
        String weaponSeries = weaponData.getWeaponSeries();
        int rarity = weaponData.getRarity();
        if (rarity == 1) {
            return null; //unused weapons
        }

        boolean isNullElement = weaponData.getElementId() == 99;

        int level = 1;
        int unbinds = 0;
        int refines = 0;
        int fiveStarSlotCount = 0;
        int sindomSlotCount = 0;
        //can't make copies of Mega Man collab weapons apparently...
        int copiesCount = weaponData.getName().contains("Mega") ? 1 : 4;
        switch (weaponSeries) {
            case "Core":
                switch (rarity) {
                    case 3:
                        level = 20;
                        break;
                    case 4:
                        level = 30;
                        break;
                    case 5:
                        level = 50;
                        break;
                }
                unbinds = 4;
                if (!isNullElement) {
                    fiveStarSlotCount = 1;
                }
                break;
            case "Void":
            case "Chimeratech":
            case "High Dragon":
                level = 70;
                unbinds = 8;
                refines = 1;
                fiveStarSlotCount = 1;
                break;
            case "Agito":
                level = 90;
                unbinds = 9;
                refines = 2;
                fiveStarSlotCount = 1;
                sindomSlotCount = 2;
                break;
            case "Primal Dragon":
                level = 80;
                unbinds = 8;
                refines = 1;
                fiveStarSlotCount = 1;
                sindomSlotCount = 2;
                break;
            case "Other":
                //hard coded /shrug
                if (weaponData.getName().contains("Mega")) {
                    level = 50;
                    unbinds = 4;
                }
                switch (weaponData.getName()) {
                    case "Soldier's Brand":
                        level = 10;
                        unbinds = 4;
                        break;
                    case "Lucky Hanetsuki Paddle":
                        level = 50;
                        unbinds = 4;
                        break;
                }
                break;
        }

        int passiveAbilityCount = weaponData.getPassiveAbilityIdList().size();
        //too lazy to figure out mapping for these abilities + no one cares honestly
        JsonArray voidWeaponAbilities = new JsonArray();

        JsonArray emptyVoidWeaponAbilities = new JsonArray();
        for (int i = 0; i < 15; i++) {
            voidWeaponAbilities.add(i < passiveAbilityCount ? 1 : 0);
            emptyVoidWeaponAbilities.add(0);
        }

        boolean minWeapon = getTime == -1 && !options.getFieldValue("maxAddedWeapons");

        if (!minWeapon) {
            out.addProperty("weapon_body_id", weaponData.getId());                      //ID
            out.addProperty("buildup_count", level);                                    //level
            out.addProperty("limit_break_count", unbinds);                              //unbinds
            out.addProperty("limit_over_count", refines);                               //refines
            out.addProperty("equipable_count", copiesCount);                            //equip count
            out.addProperty("additional_crest_slot_type_1_count", fiveStarSlotCount);   //5* slot count
            out.addProperty("additional_crest_slot_type_2_count", 0);
            out.addProperty("additional_crest_slot_type_3_count", sindomSlotCount);     //sindom slot count
            out.addProperty("additional_effect_count", 0);                         //?
            out.add("unlock_weapon_passive_ability_no_list", voidWeaponAbilities);      //void weapon abilities?
            out.addProperty("fort_passive_chara_weapon_buildup_count", weaponData.hasWeaponBonus() ? 1 : 0);        //weapon bonus
            out.addProperty("is_new", 1);
            out.addProperty("gettime", getTime == -1 ? Instant.now().getEpochSecond() : getTime);
        } else {
            out.addProperty("weapon_body_id", weaponData.getId());                      //ID
            out.addProperty("buildup_count", 0);                                    //level
            out.addProperty("limit_break_count", 0);                              //unbinds
            out.addProperty("limit_over_count", 0);                               //refines
            out.addProperty("equipable_count", 1);                            //equip count
            out.addProperty("additional_crest_slot_type_1_count", 0);   //5* slot count
            out.addProperty("additional_crest_slot_type_2_count", 0);
            out.addProperty("additional_crest_slot_type_3_count", 0);     //sindom slot count
            out.addProperty("additional_effect_count", 0);                         //?
            out.add("unlock_weapon_passive_ability_no_list", emptyVoidWeaponAbilities);      //void weapon abilities?
            out.addProperty("fort_passive_chara_weapon_buildup_count", 0);        //weapon bonus
            out.addProperty("is_new", 1);
            out.addProperty("gettime", Instant.now().getEpochSecond());
        }
        return out;
    }

    private JsonObject buildWyrmprint(WyrmprintMeta printData, int getTime) {
        JsonObject out = new JsonObject();
        int rarity = printData.getRarity();
        int level = 1;
        int augmentCount = 0;

        //to add new print as a level 1 un-upgraded print
        boolean minPrint = getTime == -1 && !options.getFieldValue("maxAddedWyrmprints");

        switch (rarity) {
            case 2:
                level = 10;
                augmentCount = 50;
                break;
            case 3:
                level = 20;
                augmentCount = 50;
                break;
            case 4:
                level = 40;
                augmentCount = 50;
                break;
            case 5:
                level = 50;
                augmentCount = 50;
                break;
            case 9: //sindom
                level = 30;
                augmentCount = 40;
                break;
        }
        if (!minPrint) {
            out.addProperty("ability_crest_id", printData.getId());
            out.addProperty("buildup_count", level);
            out.addProperty("limit_break_count", 4);
            out.addProperty("equipable_count", 4);
            out.addProperty("hp_plus_count", augmentCount);
            out.addProperty("attack_plus_count", augmentCount);
            out.addProperty("is_new", 1);
            out.addProperty("is_favorite", 0);
            out.addProperty("gettime", getTime == -1 ? Instant.now().getEpochSecond() : getTime);
        } else {
            out.addProperty("ability_crest_id", printData.getId());
            out.addProperty("buildup_count", 0);
            out.addProperty("limit_break_count", 0);
            out.addProperty("equipable_count", 0);
            out.addProperty("hp_plus_count", 0);
            out.addProperty("attack_plus_count", 0);
            out.addProperty("is_new", 1);
            out.addProperty("is_favorite", 0);
            out.addProperty("gettime", Instant.now().getEpochSecond());
        }
        return out;
    }

    private void unlockAdventurerStory(int id) {
        if (id == 10750102 || id == 10140101) {
            return; //Mega Man, Euden have no stories to tell
        }
        //Compile list of adventurer stories in savedata
        Set<Integer> ownedStories = new HashSet<>();
        getFieldAsJsonArray("data", "unit_story_list").forEach(jsonEle ->
                ownedStories.add(jsonEle.getAsJsonObject().get("unit_story_id").getAsInt()));

        List<Integer> storyIDs = adventurerStoryMap.get(id);
        for(int i = 0; i < 5; i ++){
            int storyID = storyIDs.get(i);
            if(ownedStories.contains(storyID)){
                continue; //dont add story if u already have it
            }
            JsonObject story = new JsonObject();
            story.addProperty("unit_story_id", storyID);
            story.addProperty("is_read", 0);
            getFieldAsJsonArray("data", "unit_story_list").add(story);
        }
    }

    // Hacked Utils \\

    private void addHackedUnit(int id){
        Set<Integer> ownedIdSet = getSetFromField("chara_id", "data", "chara_list");
        if(ownedIdSet.contains(id)){
            return; //dont add if u already have it
        }
        getField("data", "chara_list").getAsJsonArray().add(buildHackedUnit(id));
    }

    //Returns a built adventurer in savedata.txt format
    private JsonObject buildHackedUnit(int id) {
        //use Euden stats as default
        int hp = 716, str = 480, hasSS = 1, s1Level = 1;

        switch(id){
            case 19900001: //Zethia
                hp = 830;
                str = 466;
                s1Level = 3;
                break;
            case 19900002: //Leif (Light)
            case 19900005: //Leif (Wind)
                hp = 835;
                str = 456;
                break;
        }

        JsonObject out = new JsonObject();
        JsonArray mc = new JsonArray();
        for (int i = 1; i <= 40; i++) {
            mc.add(i);
        }
        out.addProperty("chara_id", id);
        out.addProperty("rarity", 5);
        out.addProperty("exp", 1191950);
        out.addProperty("level", 80);
        out.addProperty("additional_max_level", 0);
        out.addProperty("hp_plus_count", 100);
        out.addProperty("attack_plus_count", 100);
        out.addProperty("limit_break_count", 5);
        out.addProperty("is_new", 1);
        out.addProperty("gettime", Instant.now().getEpochSecond());
        out.addProperty("skill_1_level", s1Level);
        out.addProperty("skill_2_level", 1);
        out.addProperty("ability_1_level", 1);
        out.addProperty("ability_2_level", 1);
        out.addProperty("ability_3_level", 1);
        out.addProperty("burst_attack_level", 2);
        out.addProperty("combo_buildup_count", 0);
        out.addProperty("hp", hp);
        out.addProperty("attack", str);
        out.addProperty("ex_ability_level", 5);
        out.addProperty("ex_ability_2_level", 5);
        out.addProperty("is_temporary", 0);
        out.addProperty("is_unlock_edit_skill", hasSS);
        out.add("mana_circle_piece_id_list", mc);
        out.addProperty("list_view_flag", 1);
        return out;
    }

    private JsonObject buildHackedDragon(int id) {
        JsonObject out = new JsonObject();
        int keyIdMax = getMaxFromObjListField("dragon_key_id", "data", "dragon_list");

        out.addProperty("dragon_key_id", keyIdMax + 10);
        out.addProperty("dragon_id", id);
        out.addProperty("level", 100);
        out.addProperty("hp_plus_count", 50);
        out.addProperty("attack_plus_count", 50);
        out.addProperty("exp", 1240020);
        out.addProperty("is_lock", 0);
        out.addProperty("is_new", 1);
        out.addProperty("get_time", Instant.now().getEpochSecond());
        out.addProperty("skill_1_level", 2);
        out.addProperty("ability_1_level", 1);
        out.addProperty("ability_2_level", 1);
        out.addProperty("limit_break_count", 4);
        return out;
    }

    /// Methods \\\
    public void uncapMana() {
        writeInteger(10_000_000, "data", "user_data", "mana_point");
    }
    public void setRupies() {
        writeLong(2_000_000_000, "data", "user_data", "coin");
    }

    public void plunderDonkay() {
        writeInteger(710_000, "data", "user_data", "crystal");

        int keyIdMax = 0;
        JsonArray ticketsList = getFieldAsJsonArray("data", "summon_ticket_list");
        boolean foundSingles = false;
        boolean foundTenfolds = false;
        for (JsonElement jsonEle : ticketsList){
            JsonObject ticketCount = jsonEle.getAsJsonObject();
            int id = ticketCount.get("summon_ticket_id").getAsInt();
            int keyId = ticketCount.get("key_id").getAsInt();
            int quantity = ticketCount.get("quantity").getAsInt();

            if(id == 10101){
                foundSingles = true;
                if(quantity < 2600){
                    ticketCount.remove("quantity");
                    ticketCount.addProperty("quantity", 2600);
                }
            } else if(id == 10102){
                foundTenfolds = true;
                if(quantity < 170){
                    ticketCount.remove("quantity");
                    ticketCount.addProperty("quantity", 170);
                }
            }
            keyIdMax = Math.max(keyIdMax, keyId);
        }
        //eh
        if(!foundSingles){
            JsonObject newTicketCount = new JsonObject();
            newTicketCount.addProperty("key_id", keyIdMax + 200);
            newTicketCount.addProperty("summon_ticket_id", 10101);
            newTicketCount.addProperty("quantity", 2600);
            newTicketCount.addProperty("use_limit_time", 0);
            ticketsList.add(newTicketCount);
        }
        if(!foundTenfolds){
            JsonObject newTicketCount = new JsonObject();
            newTicketCount.addProperty("key_id", keyIdMax + 400);
            newTicketCount.addProperty("summon_ticket_id", 10102);
            newTicketCount.addProperty("quantity", 170);
            newTicketCount.addProperty("use_limit_time", 0);
            ticketsList.add(newTicketCount);
        }
    }

    public void battleOnTheByroad() {
        writeInteger(10_000_000, "data", "user_data", "dew_point");
    }

    public int addMissingAdventurers() {
        int count = 0;

        //Compile a list of ID's you have
        Set<Integer> ownedIdSet = getSetFromField("chara_id", "data", "chara_list");

        //Go through a list of all the adventurers in the game
        for(Map.Entry<Integer, AdventurerMeta> entry : idToAdventurer.entrySet()){
            int id = entry.getKey();
            AdventurerMeta adventurer = entry.getValue();
            if (!ownedIdSet.contains(id)) { //If you don't own this adventurer
                //Construct new unit (Does this unit have a mana spiral?)
                JsonObject newUnit = buildUnit(adventurer, -1);
                //Add it to your roster
                if (newUnit != null) {
                    getField("data", "chara_list").getAsJsonArray().add(newUnit);
                    unlockAdventurerStory(id);
                    addAdventurerEncyclopediaBonus(adventurer);
                    count++;
                    write(adventurer.getName());
                }
            }
        }
        flushLog("Added adventurers");
        return count;
    }

    public void addAdventurer(String advName) {
        AdventurerMeta advData = nameToAdventurer.get(advName);
        if (advData == null) {
            System.out.println("Can't find adventurer with name '" + advName + "'. Try again!");
            return;
        }
        //Compile a list of ID's you have
        Set<Integer> ownedIdSet = getSetFromField("chara_id", "data", "chara_list");

        int id = advData.getId();
        String name = advData.getName();
        if (ownedIdSet.contains(id)) {
            System.out.println("You already own '" + name + "'!");
            return;
        }
        //Construct new unit (Does this unit have a mana spiral?)
        JsonObject newUnit = buildUnit(advData, -1);
        //Add it to your roster
        if (newUnit != null) {
            getField("data", "chara_list").getAsJsonArray().add(newUnit);
            unlockAdventurerStory(id);
            addAdventurerEncyclopediaBonus(advData);
            System.out.println("Added '" + name + "'!");
        }
    }

    public int addMissingWyrmprints() {
        int count = 0;
        //Compile a list of ID's you have
        Set<Integer> ownedIdSet = getSetFromField("ability_crest_id", "data", "ability_crest_list");

        //Go through a list of all the wyrmprints in the game
        for (Map.Entry<Integer, WyrmprintMeta> entry : idToPrint.entrySet()) {
            WyrmprintMeta wyrmprint = entry.getValue();
            int id = entry.getKey();
            if (!ownedIdSet.contains(id)) { //If you don't own this print
                //Construct new print
                JsonObject newPrint = buildWyrmprint(wyrmprint, -1);
                //Add it to your inventory
                getField("data", "ability_crest_list").getAsJsonArray().add(newPrint);
                count++;
                write(wyrmprint.getName() + "(" + wyrmprint.getRarity() + "*)");
            }
        }
        flushLog("Added wyrmprints");
        return count;
    }

    //return response message
    public String addMissingDragons(boolean toExcludeLowRarityDragons) {
        int count = 0;
        int expandAmount = 0;
        int keyIdMax = getMaxFromObjListField("dragon_key_id", "data", "dragon_list");

        //Compile a list of ID's you have
        Set<Integer> ownedIdSet = getSetFromField("dragon_id", "data", "dragon_list");
        //Compile a list of ID's from your encyclopedia
        Set<Integer> albumIDSet = getSetFromField("dragon_id", "data", "album_dragon_list");

        //Go through a list of all the dragons in the game
        for (Map.Entry<Integer, DragonMeta> entry : idToDragon.entrySet()) {
            DragonMeta dragon = entry.getValue();
            int id = dragon.getId();
            int rarity = dragon.getRarity();
            if (toExcludeLowRarityDragons && (rarity == 3 || rarity == 4)) {
                continue; //maybe ignore low rarity dragons
            }
            if (!ownedIdSet.contains(id)) { //If you don't own this dragon
                //Construct new dragon (Does this dragon have 5UB?)
                JsonObject newDragon = buildDragon(dragon, keyIdMax, count + 1);
                //Add it to your roster
                int dragonListSize = getFieldAsJsonArray("data", "dragon_list").size();
                int dragonListCapacity = getFieldAsInt("data", "user_data", "max_dragon_quantity");
                if (dragonListSize == dragonListCapacity) {           //if dragon roster is full...
                    if (dragonListCapacity == MAX_DRAGON_CAPACITY) {  //if dragon capacity is maxed... can't do anything
                        return "Dragon roster capacity is maxed! Unable to add new dragons...";
                    } else {                                        //expand dragon capacity if able to
                        writeInteger(dragonListCapacity + 5, "data", "user_data", "max_dragon_quantity");
                        expandAmount += 5;
                    }
                }
                getFieldAsJsonArray("data", "dragon_list").add(newDragon);

                //If you've never owned this dragon before
                if (!albumIDSet.contains(id)) {
                    //Add to encyclopedia
                    getFieldAsJsonArray("data", "album_dragon_list").add(buildDragonAlbumData(dragon));
                    addDragonEncyclopediaBonus(dragon);
                    //Add dragon bond obj
                    if (id != 20050522) { //Arsene check
                        JsonObject dragonBond = new JsonObject();
                        dragonBond.addProperty("dragon_id", id);
                        dragonBond.addProperty("gettime", Instant.now().getEpochSecond());
                        dragonBond.addProperty("reliability_level", 30);
                        dragonBond.addProperty("reliability_total_exp", 36300);
                        dragonBond.addProperty("last_contact_time", Instant.now().getEpochSecond());
                        getField("data", "dragon_reliability_list").getAsJsonArray().add(dragonBond);
                    }
                }
                count++;
                write(dragon.getName() + "(" + dragon.getRarity() + "*)");
            }
        }
        flushLog("Added dragons");
        return expandAmount == 0 ?
                "Added " + count + " missing dragons." :
                "Added " + count + " missing dragons. Dragon inventory capacity was raised by " + expandAmount + ".";
    }

    public void addDragon(String drgName) {
        int expandAmount = 0;
        int keyIdMax = getMaxFromObjListField("dragon_key_id", "data", "dragon_list");

        //Compile a list of ID's from your encyclopedia
        Set<Integer> albumIDSet = getSetFromField("dragon_id", "data", "album_dragon_list");

        DragonMeta drgData = nameToDragon.get(drgName);
        if (drgData == null) {
            System.out.println("Can't find dragon with name '" + drgName + "'. Try again!");
            return;
        }
        int id = drgData.getId();
        String name = drgData.getName();

        //Construct new dragon (Does this dragon have 5UB?)
        JsonObject newDragon = buildDragon(drgData, keyIdMax, 1);
        //Add it to your roster
        int dragonListSize = getFieldAsJsonArray("data", "dragon_list").size();
        int dragonListCapacity = getFieldAsInt("data", "user_data", "max_dragon_quantity");
        if (dragonListSize == dragonListCapacity) {           //if dragon roster is full...
            if (dragonListCapacity == MAX_DRAGON_CAPACITY) {  //if dragon capacity is maxed... can't do anything
                System.out.println("Dragon roster capacity is maxed! Unable to add new dragons...");
                return;
            } else {                                        //expand dragon capacity if able to
                writeInteger(dragonListCapacity + 5, "data", "user_data", "max_dragon_quantity");
                expandAmount += 5;
            }
        }
        getFieldAsJsonArray("data", "dragon_list").add(newDragon);

        //If you've never owned this dragon before
        if (!albumIDSet.contains(id)) {
            //Add to encyclopedia
            getFieldAsJsonArray("data", "album_dragon_list").add(buildDragonAlbumData(drgData));
            addDragonEncyclopediaBonus(drgData);
            //Add dragon bond obj
            if (id != 20050522) { //Arsene check
                JsonObject dragonBond = new JsonObject();
                dragonBond.addProperty("dragon_id", id);
                dragonBond.addProperty("gettime", Instant.now().getEpochSecond());
                dragonBond.addProperty("reliability_level", 1);
                dragonBond.addProperty("reliability_total_exp", 0);
                dragonBond.addProperty("last_contact_time", Instant.now().getEpochSecond());
                getField("data", "dragon_reliability_list").getAsJsonArray().add(dragonBond);
            }
        }

        String out = expandAmount == 0 ?
                "Added '" + name + "'!" :
                "Added '" + name + "'! Dragon inventory capacity was raised by " + expandAmount + ".";
        System.out.println(out);
    }

    public void addItems() {
        JsonArray items = getFieldAsJsonArray("data", "material_list");
        for (JsonElement jsonEle : items) {
            JsonObject jsonObj = jsonEle.getAsJsonObject();
            int count = jsonObj.get("quantity").getAsInt();
            if (count <= 30000) {
                jsonObj.remove("quantity");
                jsonObj.addProperty("quantity", 30000);
            }
        }
        Set<Integer> ownedIdSet = getSetFromField("material_id", "data", "material_list");
        for(Map.Entry<Integer, MaterialMeta> entry : idToMaterial.entrySet()){
            int id = entry.getKey();
            MaterialMeta mat = entry.getValue();
            if(!ownedIdSet.contains(id)){
                switch(mat.getCategory()){ //ignore certain items
                    case "Raid":
                    case "Raid, Collab":
                    case "Battle Royale":
                    case "Idk":
                        continue;
                }
                JsonObject newItem = new JsonObject();
                newItem.addProperty("material_id", id);
                newItem.addProperty("quantity", 30000);
                items.add(newItem);
                write(mat.getName());
            }
        }
        flushLog("Added materials");

        int[] giftIds = new int[]{30001, 30002, 30003, 40001};
        JsonArray giftList = new JsonArray();
        for (Integer giftId : giftIds) {
            JsonObject gift = new JsonObject();
            gift.addProperty("dragon_gift_id", giftId);
            gift.addProperty("quantity", 3000);
            giftList.add(gift);
        }
        getFieldAsJsonObject("data").remove("dragon_gift_list");
        getFieldAsJsonObject("data").add("dragon_gift_list", giftList);

        flushLog("Added dragon gifts");
    }

    public void backToTheMines() {
        //for each kscape combo, put new kscape print data for each ele-weapon combo

        String[][] kscapeCombos = KscapeCombos.KSCAPES;
        String[][] kscapeLabels = KscapeCombos.KSCAPE_LABELS;
        int keyIdOffset = 1;
        JsonObject jsonData = getField("data").getAsJsonObject();
        jsonData.remove("talisman_list");
        JsonArray talismans = new JsonArray();
        for (String[] kscapeCombo : kscapeCombos) {
            //for each ele-wep combo
            for (String[] kscapeLabel : kscapeLabels) {
                for (String label : kscapeLabel) {
                    talismans.add(buildTalisman(label, kscapeCombo, keyIdOffset));
                    keyIdOffset++;
                }
            }

        }
        jsonData.add("talisman_list", talismans);

        //delete equipped kscapes, since old kscape ID's will now point to
        //a kscape that no longer exists
        JsonArray partyList = getFieldAsJsonArray("data", "party_list");
        for (JsonElement jsonEle : partyList) {
            JsonObject party = jsonEle.getAsJsonObject();
            for (JsonElement jsonEle2 : party.getAsJsonArray("party_setting_list")) {
                JsonObject adventurer = jsonEle2.getAsJsonObject();
                adventurer.remove("equip_talisman_key_id");
                adventurer.addProperty("equip_talisman_key_id", 0);
            }
        }
    }

    public int addMissingWeaponSkins() {
        int count = 0;
        Set<Integer> ownedWeaponSkinIDs = getSetFromField("weapon_skin_id", "data", "weapon_skin_list");

        for (Map.Entry<Integer, String> entry : idToWeaponSkinName.entrySet()) {
            int weaponSkinId = entry.getKey();
            if (!ownedWeaponSkinIDs.contains(weaponSkinId)) {
                JsonObject newWeaponSkin = new JsonObject();
                newWeaponSkin.addProperty("weapon_skin_id", weaponSkinId);
                newWeaponSkin.addProperty("is_new", 1);
                newWeaponSkin.addProperty("gettime", Instant.now().getEpochSecond());
                getFieldAsJsonArray("data", "weapon_skin_list").add(newWeaponSkin);
                count++;
                write(entry.getValue().replace(" (Skin)", ""));
            }
        }
        flushLog("Added weapon skins");
        return count;
    }

    public int addMissingWeapons() {
        int count = 0;
        //Compile a set of ID's you have
        Set<Integer> ownedIdSet = getSetFromField("weapon_body_id", "data", "weapon_body_list");

        //Go through a list of all the weapons in the game
        for (Map.Entry<Integer, WeaponMeta> entry : idToWeapon.entrySet()) {
            WeaponMeta weapon = entry.getValue();
            int id = entry.getKey();
            if (!ownedIdSet.contains(id)) { //If you don't own this weapon
                //Construct new weapon
                JsonObject newWeapon = buildWeapon(weapon, -1);
                //Add it to your inventory
                if (newWeapon != null) {
                    getField("data", "weapon_body_list").getAsJsonArray().add(newWeapon);
                    if (options.getFieldValue("maxAddedWeapons")) {
                        //Update weapon bonuses
                        addWeaponBonus(weapon);
                        //Update weapon passives
                        updateWeaponPassives(weapon);
                    }
                    count++;
                    write(weapon.getName() + "(" + weapon.getRarity() + "*, " + weapon.getWeaponSeries() + ")");
                }
            }
        }
        flushLog("Added weapons");

        testFlags.add("addMissingWeapons");
        return count;
    }

    //doozy
    public void maxFacilities(){
        int upgradedExistingCount = 0;
        int addedCount = 0;
        int addedDecoCount = 0;

        int keyIdMax = 0;   //need to keep track of keyId so we don't run into dupe keyId issue

        JsonArray newFacilities = new JsonArray();
        JsonArray currentFacilities = getFieldAsJsonArray("data", "build_list");
        HashMap<Integer, Integer> idToBuildCount = new HashMap<>();
        //first pass... upgrade all existing facilities, and count how many of each facility you own
        //also keep track of keyIdMax
        for(JsonElement jsonEle : currentFacilities){
            JsonObject currentFacility = jsonEle.getAsJsonObject();
            int keyId = currentFacility.get("build_id").getAsInt();
            int x = currentFacility.get("position_x").getAsInt();
            int y = currentFacility.get("position_z").getAsInt();
            int id = currentFacility.get("plant_id").getAsInt();
            int level = currentFacility.get("level").getAsInt();
            //check if this facility is maxed
            FacilityMeta fac = idToFacility.get(id);
            if(level != fac.getMaxLevel()){
                upgradedExistingCount++;
                write(fac.getName() + ": " + level + " -> " + fac.getMaxLevel());
            }
            keyIdMax = Math.max(keyIdMax, keyId);
            if(idToBuildCount.containsKey(id)){ //increment build count
                int buildCount = idToBuildCount.get(id);
                idToBuildCount.put(id, buildCount + 1);
            } else {
                idToBuildCount.put(id, 1);
            }
            newFacilities.add(buildFacility(idToFacility.get(id), keyId, x, y));
        }
        flushLog("Levelled up " + upgradedExistingCount + " facilities");

        //below... might run into issues where players might end up having like 3 or 4 dojos...
        //possibly may want to remove adding missing facilities later

        //compile list of max amounts you can have for each facility
        HashMap<Integer, Integer> idToMaxBuildCount = new HashMap<>();
        idToFacility.forEach((id, fac) -> idToMaxBuildCount.put(id, fac.getMaxBuildCount()));

        //get diffs for owned fac count and max fac count
        HashMap<Integer, Integer> idToMaxBuildCountDiff = new HashMap<>();
        for(Map.Entry<Integer, Integer> entry : idToMaxBuildCount.entrySet()){
            int id = entry.getKey();
            int maxCount = entry.getValue();
            int ownedCount = idToBuildCount.getOrDefault(id, 0);
            idToMaxBuildCountDiff.put(id, maxCount - ownedCount);
        }

        //second pass... add appropriate amount of each facility missing
        for(Map.Entry<Integer, Integer> entry : idToMaxBuildCountDiff.entrySet()){
            int id = entry.getKey();
            int missingCount = entry.getValue();
            for(int i = 0; i < missingCount; i++){
                newFacilities.add(buildFacility(idToFacility.get(id), keyIdMax, addedCount + 1));
                if(idToFacility.get(id).getMaxLevel() == 0){ //max level 0 --> deco
                    addedDecoCount++;
                } else {
                    addedCount++;
                }
            }
            if(missingCount > 0){
                write(idToFacility.get(id).getName() + " x" + missingCount);
            }
        }
        flushLog("Added facilities");
        System.out.println("Upgraded " + upgradedExistingCount + " existing facilities, added " + addedCount +
                " new facilities, and added " + addedDecoCount + " decoration facilities");
        //replace facilities list
        getFieldAsJsonObject("data").remove("build_list");
        getFieldAsJsonObject("data").add("build_list", newFacilities);

        //if you probably got a stat boost, update fort_bonus_list
        //...hardcoded for now
        if(upgradedExistingCount != 0 || addedCount != 0){
            JsonObject bonuses = getFieldAsJsonObject("data", "fort_bonus_list");
            bonuses.remove("param_bonus"); //facility weapon bonuses
            bonuses.remove("element_bonus");
            bonuses.remove("dragon_bonus");
            bonuses.add("param_bonus", maxedFacilityBonuses.get("param_bonus"));
            bonuses.add("element_bonus", maxedFacilityBonuses.get("element_bonus"));
            bonuses.add("dragon_bonus", maxedFacilityBonuses.get("dragon_bonus"));
        }
        //update fort_plant_list... hardcoded for now
        //wtf does this do anyway?
        getFieldAsJsonObject("data").remove("fort_plant_list");
        getFieldAsJsonObject("data").add("fort_plant_list", maxedFacilityBonuses.get("fort_plant_list"));
    }

    public void maxAdventurers() {
        JsonArray updatedAdventurers = new JsonArray();
        JsonArray ownedAdventurers = getFieldAsJsonArray("data", "chara_list");

        for(JsonElement jsonEle : ownedAdventurers){
            JsonObject ownedAdventurer = jsonEle.getAsJsonObject();
            int id = ownedAdventurer.get("chara_id").getAsInt();
            int getTime = ownedAdventurer.get("gettime").getAsInt();
            AdventurerMeta adventurer = idToAdventurer.get(id);
            if(adventurer == null){
                continue;
            }
            //Construct new unit
            JsonObject updatedUnit = buildUnit(adventurer, getTime);
            updatedAdventurers.add(updatedUnit);
            //Update encyclopedia bonus
            int level = ownedAdventurer.get("level").getAsInt();
            int mc = ownedAdventurer.get("mana_circle_piece_id_list").getAsJsonArray().size();
            int elementId = adventurer.getElementId();
            boolean hasManaspiral = adventurer.hasManaSpiral();
            double hpBonus = 0.0;
            double strBonus = 0.0;
            if (hasManaspiral) {
                if (level < 80) { hpBonus = 0.2; }
                else if (level < 100) { hpBonus = 0.1; }
                if (mc < 50) { strBonus = 0.2; }
                else if (mc < 70) { strBonus = 0.1; }
            } else {
                if (level < 80) { hpBonus = 0.1; }
                if (mc < 50) { strBonus = 0.1; }
            }
            addAdventurerEncyclopediaBonus(elementId, hpBonus, strBonus);
            //Unlock adventurer stories
            unlockAdventurerStory(id);
        }
        //Replace current adventurer list
        getFieldAsJsonObject("data").remove("chara_list");
        getFieldAsJsonObject("data").add("chara_list", updatedAdventurers);
    }

    public void maxDragons(){
        JsonArray updatedDragons = new JsonArray();
        JsonArray ownedDragons = getFieldAsJsonArray("data", "dragon_list");

        for(JsonElement jsonEle : ownedDragons){
            JsonObject ownedDragon = jsonEle.getAsJsonObject();
            int id = ownedDragon.get("dragon_id").getAsInt();
            int getTime = ownedDragon.get("get_time").getAsInt();
            int keyId = ownedDragon.get("dragon_key_id").getAsInt();
            DragonMeta dragon = idToDragon.get(id);
            if(dragon == null){
                continue;
            }
            //Construct new dragon
            JsonObject updatedUnit = buildDragon2(dragon, keyId, getTime);
            updatedDragons.add(updatedUnit);
            boolean has5UB = dragon.has5UB();

            //Update encyclopedia max level/unbound obj
            for (JsonElement jsonEle2 : getFieldAsJsonArray("data", "album_dragon_list")){
                JsonObject encycloData = jsonEle2.getAsJsonObject();
                if(encycloData.get("dragon_id").getAsInt() != id){
                    continue; //ignore if this isnt the dragon
                }
                int maxLevel = encycloData.get("max_level").getAsInt();
                int maxUnbinds = encycloData.get("max_limit_break_count").getAsInt();
                boolean toUpdateBonuses = maxUnbinds < (has5UB ? 5 : 4); //actual bonuses itself
                boolean toUpdateEncyclo = toUpdateBonuses || maxLevel < dragon.getMaxLevel(); //record obj in album_dragon_list

                if(toUpdateEncyclo){
                    encycloData.remove("max_level");
                    encycloData.remove("max_limit_break_count");
                    encycloData.addProperty("max_level", dragon.getMaxLevel());
                    encycloData.addProperty("max_limit_break_count", has5UB ? 5 : 4);
                    if(toUpdateBonuses){
                        //Update encyclopedia bonus
                        int unbinds = ownedDragon.get("limit_break_count").getAsInt();
                        int elementId = dragon.getElementId();
                        double hpBonus = 0.0;
                        if (has5UB) {
                            if (unbinds < 4) { hpBonus = 0.2; }
                            else if (unbinds < 5) { hpBonus = 0.1; }
                        } else {
                            if (unbinds < 4) { hpBonus = 0.1; }
                        }
                        addDragonEncyclopediaBonus(elementId, hpBonus, 0.0);
                    }
                }
            }
        }
        //Replace current dragon list
        getFieldAsJsonObject("data").remove("dragon_list");
        getFieldAsJsonObject("data").add("dragon_list", updatedDragons);

    }

    public void maxWeapons(){
        JsonArray updatedWeapons = new JsonArray();
        JsonArray ownedWeapons = getFieldAsJsonArray("data", "weapon_body_list");

        for(JsonElement jsonEle : ownedWeapons){
            JsonObject ownedWeapon = jsonEle.getAsJsonObject();
            int id = ownedWeapon.get("weapon_body_id").getAsInt();
            int getTime = ownedWeapon.get("gettime").getAsInt();
            WeaponMeta weapon = idToWeapon.get(id);
            //Construct new weapon
            JsonObject updatedWeapon = buildWeapon(weapon, getTime);
            updatedWeapons.add(updatedWeapon);
            //Update weapon bonus
            boolean isWeaponBonusUnlocked = ownedWeapon.get("fort_passive_chara_weapon_buildup_count").getAsInt() == 1;
            if(!isWeaponBonusUnlocked){
                addWeaponBonus(weapon);
            }
            //Update weapon passives
            updateWeaponPassives(weapon);
        }
        //Replace current weapon list
        getFieldAsJsonObject("data").remove("weapon_body_list");
        getFieldAsJsonObject("data").add("weapon_body_list", updatedWeapons);

        testFlags.add("maxWeapons");
    }

    public void updateWeaponPassives(WeaponMeta weapon) {
        List<Integer> passiveIdList = weapon.getPassiveAbilityIdList();
        for(Integer id : passiveIdList) {
            JsonObject passiveId = new JsonObject();
            passiveId.addProperty("weapon_passive_ability_id", id);
            JsonArray passiveAbilityList = getFieldAsJsonArray("data", "weapon_passive_ability_list");
            if(!passiveAbilityList.contains(passiveId)){
                passiveAbilityList.add(passiveId);
            }
        }
    }

    public void maxWyrmprints(){
        JsonArray updatedWyrmprints = new JsonArray();
        JsonArray ownedWyrmprints = getFieldAsJsonArray("data", "ability_crest_list");

        for(JsonElement jsonEle : ownedWyrmprints){
            JsonObject ownedWyrmprint = jsonEle.getAsJsonObject();
            int id = ownedWyrmprint.get("ability_crest_id").getAsInt();
            int getTime = ownedWyrmprint.get("gettime").getAsInt();
            WyrmprintMeta wyrmprint = idToPrint.get(id);
            //Construct new print
            JsonObject updatedPrint = buildWyrmprint(wyrmprint, getTime);
            updatedWyrmprints.add(updatedPrint);
        }
        //Replace current adventurer list
        getFieldAsJsonObject("data").remove("ability_crest_list");
        getFieldAsJsonObject("data").add("ability_crest_list", updatedWyrmprints);
    }

    //check for temporary adventurers who've been skipped
    //their list_view_flag will be == 0
    public List<String> checkSkippedTempAdventurers(){
        List<String> skippedAdvs = new ArrayList<>();
        for(JsonElement jsonEle : getFieldAsJsonArray("data", "chara_list")){
            JsonObject adv = jsonEle.getAsJsonObject();
            if(adv.get("list_view_flag").getAsInt() == 0){
                skippedAdvs.add(idToAdventurer.get(adv.get("chara_id").getAsInt()).getName());
            }
        }
        return skippedAdvs;
    }

    //set list_view_flag of all adventurers to 1
    public void setAdventurerVisibleFlags(){
        for(JsonElement jsonEle : getFieldAsJsonArray("data", "chara_list")){
            JsonObject adv = jsonEle.getAsJsonObject();
            if(adv.get("list_view_flag").getAsInt() == 0){
                adv.remove("list_view_flag");
                adv.addProperty("list_view_flag", 1);
            }
        }
    }

    //Hacked options

    public void addTutorialZethia(){
        addHackedUnit(19900001);
    }

    public void addStoryLeifs(){
        addHackedUnit(19900002);
        addHackedUnit(19900005);
    }

    public void addGunnerCleo(){
        addHackedUnit(99900009); //Gunner Cleo
    }

    public void addNotteAndDog(){
        addHackedUnit(19900003); //Yellow Notte
        addHackedUnit(19900004); //Puppy
        addHackedUnit(19900006); //Blue Notte
    }

    public void addStoryNPCs(){
        for(int i = 0; i < 67; i++){
            addHackedUnit(19100001 + i);
        }
    }

    public void addOthers(){ //this shouldn't be used tbh
        addNotteAndDog();
        addStoryNPCs();
        addABR3Stars();
        addUnplayableDragons();
    }

    public void addABR3Stars(){
        for(int i = 0; i < 9; i++){
            addHackedUnit(99130001 + i * 100000);
        }
    }

    public void addUniqueShapeshiftDragons(){
        Arrays.asList(29900006, 29900014, 29900017, 29900018, 29900023).forEach(
                id -> getField("data", "dragon_list").getAsJsonArray().add(buildHackedDragon(id)));
    }

    public void addUnplayableDragons(){
        for(int i = 0; i < 27; i++){
            getField("data", "dragon_list").getAsJsonArray().add(buildHackedDragon(29900001 + i));
        }
        getField("data", "dragon_list").getAsJsonArray().add(buildHackedDragon(29800001));
        getField("data", "dragon_list").getAsJsonArray().add(buildHackedDragon(29800002));
        getField("data", "dragon_list").getAsJsonArray().add(buildHackedDragon(29800003));
        for(int i = 0; i < 6; i++){
            getField("data", "dragon_list").getAsJsonArray().add(buildHackedDragon(21000001 + i));
        }
        getField("data", "dragon_list").getAsJsonArray().add(buildHackedDragon(29940301));
        getField("data", "dragon_list").getAsJsonArray().add(buildHackedDragon(29950405));
        getField("data", "dragon_list").getAsJsonArray().add(buildHackedDragon(29950116));
        getField("data", "dragon_list").getAsJsonArray().add(buildHackedDragon(29950522));
        getField("data", "dragon_list").getAsJsonArray().add(buildHackedDragon(29950317));
        getField("data", "dragon_list").getAsJsonArray().add(buildHackedDragon(29950523));
        getField("data", "dragon_list").getAsJsonArray().add(buildHackedDragon(29950518));
        getField("data", "dragon_list").getAsJsonArray().add(buildHackedDragon(29950415));
        getField("data", "dragon_list").getAsJsonArray().add(buildHackedDragon(29950524));
        getField("data", "dragon_list").getAsJsonArray().add(buildHackedDragon(29950416));
        getField("data", "dragon_list").getAsJsonArray().add(buildHackedDragon(29950525));
        getField("data", "dragon_list").getAsJsonArray().add(buildHackedDragon(29950121));
        getField("data", "dragon_list").getAsJsonArray().add(buildHackedDragon(29950320));
    }

    //ehh......
    public void deleteDupeIds () {
        // soem guy downloaded save data from orchis and found out that
        // they had a lot of dupe weapon skin ids. probably cause they
        // used this program to edit them in, and then made some weapons in the server
        // so lets just delete the dupe ids
        int dupeDragonKeyIdCount = 0;
        int dupeWeaponSkinIdCount = 0;

        // happened with dragon key ids and weapon skin ids...
        List<Integer> keyIds = new ArrayList<>();
        List<JsonElement> toRemove = new ArrayList<>();
        JsonArray dragons = getFieldAsJsonArray("data", "dragon_list");
        for (JsonElement jsonEle : dragons) {
            JsonObject dragon = jsonEle.getAsJsonObject();
            int id = dragon.get("dragon_key_id").getAsInt();
            if (keyIds.contains(id)) {
                dupeDragonKeyIdCount++;
                toRemove.add(dragon);
            } else {
                keyIds.add(id);
            }
        }
        for (JsonElement badDragon : toRemove) {
            dragons.remove(badDragon);
        }

        keyIds.clear();
        toRemove.clear();
        JsonArray weaponSkins = getFieldAsJsonArray("data", "weapon_skin_list");
        for (JsonElement jsonEle : weaponSkins) {
            JsonObject weaponSkin = jsonEle.getAsJsonObject();
            int id = weaponSkin.get("weapon_skin_id").getAsInt();
            if (keyIds.contains(id)) {
                dupeWeaponSkinIdCount++;
                toRemove.add(weaponSkin);
            } else {
                keyIds.add(id);
            }
        }
        for (JsonElement badWeaponSkin : toRemove) {
            weaponSkins.remove(badWeaponSkin);
        }
        
        if(dupeDragonKeyIdCount != 0 || dupeWeaponSkinIdCount != 0) {
            System.out.println("Found dupe ID issues with the save file when importing... this should not happen."
                    + " The save editor will remove these dupe ID's for editing.");
            if (dupeDragonKeyIdCount != 0) {
                System.out.println("Error: Duplicate dragon key ID count: " +  dupeDragonKeyIdCount);
            }
            if (dupeWeaponSkinIdCount != 0) {
                System.out.println("Error: Duplicate weapon skin key ID count: " + dupeWeaponSkinIdCount);
                System.out.println("(This one was most likely caused by downloading save data " +
                        "from a private server that you crafted weapons on");
                System.out.println("after editing in weapon skins" +
                        " using this save editor.)");
            }
            System.out.println();
        }
    }

    public void kscapeRandomizer() {
        JsonArray talismans = new JsonArray();

        for (int i = 1; i <= 500; i++) {
            //get random adventurer portrait ID
            int portraitListSize = kscapePortraitIDs.size();
            int portraitID = kscapePortraitIDs.get(rng.nextInt(portraitListSize));

            //get random talisman
            JsonObject randomTalisman = buildRandomTalisman(portraitID, i);
            talismans.add(randomTalisman);
        }

        getField("data").getAsJsonObject().remove("talisman_list");
        getField("data").getAsJsonObject().add("talisman_list", talismans);

        //delete equipped kscapes, since old kscape ID's will now point to
        //a kscape that no longer exists
        JsonArray partyList = getFieldAsJsonArray("data", "party_list");
        for (JsonElement jsonEle : partyList) {
            JsonObject party = jsonEle.getAsJsonObject();
            for (JsonElement jsonEle2 : party.getAsJsonArray("party_setting_list")) {
                JsonObject adventurer = jsonEle2.getAsJsonObject();
                adventurer.remove("equip_talisman_key_id");
                adventurer.addProperty("equip_talisman_key_id", 0);
            }
        }
    }

    public void addGoofyKscapes() {
        addTalisman("xander", 805, 806, 721, 4); //(Water) Skill Recharge +65%, Skill Prep +100%
        addTalisman("gatov", 100100204, 100100205, 1237, 4); //ar10 + ar20 + flame ar20 
        addTalisman("syasu", 100100204, 100100205, 1225, 4); //ar10 + ar20 + hp70 ar20
        addTalisman("ranzal", 2172, 2175, 721, 4); //bolk
        addTalisman("alia", 1237, 400000822, 400000821, 1); //dyilia
        addTalisman("valyx", 2664, 871, 721, 2); //valyx
        addTalisman("emile", 2579, 2578, 806, 2); //emile
        addTalisman("klaus", 2735, 42960, 721, 1); //ned
        addTalisman("marth", 927, 929, 934, 1); //triple Last (buffer)
        addTalisman("sharena", 902, 746, 934, 1); //triple last (dmg)
        //credit: sinkarth
        addTalisman("galex", 291, 340000132, 934, 1); //"Galex Mega Fod"
        addTalisman("grace", 340000070, 340000134, 927, 1); //"Grace Last Boost"
        addTalisman("xainfried", 2735, 3701, 43160, 1); //"Super Dragon Time"
        addTalisman("yaten", 2663, 2264, 871, 1); //"Energized Boost"
        addTalisman("nino", 100100205, 340000132, 924, 1); //"I tried to fix Nino"
        addTalisman("alia", 2045, 2041, 1447, 1); //"Infinite Critical Damage"
        addTalisman("ao", 340000030, 340000132, 827, 1); //"Better Mars"
        addTalisman("dynef", 1620, 2281, 1440, 1); //"Flurry Freezer & other combo effects"
        addTalisman("grimnir", 1914, 1939, 1966, 1); //"Passive Damage Stacking"
        //credit: Klaus
        addTalisman("delphi", 456, 457, 747, 3); //negative str
        //Credits: NSS Prince
        addTalisman ("gleon", 330000108, 110070007, 1605, 1); //Mommy issues machinegun
        addTalisman ("Yukimura", 330000555, 1458, 330000555, 1); //MonkeyMachineGun
    }

    //Logs
    private List<List<String>> log = new ArrayList<>();
    private List<String> logKindaStream = new ArrayList<>();

    public static String listPrettify(List<String> list){
        int size = list.size();
        StringBuilder sb = new StringBuilder();
        for(int i = 0; i < size; i++){
            sb.append(list.get(i));
            if(i != size - 1){
                sb.append(", ");
            }
        }
        return sb.toString();
    }
    public static List<String> toList(List<String> list){
        int size = list.size();
        List<String> out = new ArrayList<>();
        StringBuilder sb = new StringBuilder();
        for(int i = 0; i < size; i++){
            sb.append(list.get(i));
            if(i != size - 1){
                sb.append(", ");
            }
            if(sb.length() > 110){
                out.add(sb.toString());
                sb.delete(0, sb.length());
            }
        }
        if(sb.length() > 0){
            out.add(sb.toString());
        }
        return out;
    }

    private void log(String message){
        log.add(Collections.singletonList(message));
    }

    public void write(String message){
        logKindaStream.add(message);
    }

    private void flushLog(String message){
        log.add(Collections.singletonList(message + ": "));
        log.add(toList(logKindaStream));
        logKindaStream.clear();
    }

    public void clearLogs(){
        log.clear();
    }

    public void printLogs(){
        for (int i = 0; i < log.size(); i++){
            List<String> messages = log.get(i);
            for (String message : messages) {
                System.out.println("[" + (i+1) + "] " + message);
            }
        }
    }

    //

    public static boolean checkIfJsonObject(String path) {
        JsonReader reader = null;
        try {
            reader = new JsonReader(new FileReader(path));
        } catch (FileNotFoundException ignored) {
            System.out.println("JSON data not found at this filepath");
            return false;
        }

        try {
            GSON.fromJson(reader, JsonObject.class);
        } catch (JsonSyntaxException e) {
            System.out.println("savedata.txt does not appear to be in JSON format!");
            return false;
        }
        return true;
    }

    // Options //

    public void editOption(String fieldName, boolean fieldValue) {
        options.editOption(fieldName, fieldValue);
    }

    public void exportOptions() {
        options.export();
    }

    public boolean hasOptions() {
        return new File(optionsPath).exists();
    }

    public void createNewOptionsFile () {
        options.export(); //when no options file found, default options are used --> export those
    }

    public boolean toPromptEditOptions () {
        if (options.hasMissingOptions()) {
            System.out.println("Missing options found... prompting editing save edit options");
            return true;
        }
        return options.toPromptEditOptions();

    }

    public Options getOptions () {
        return options;
    }

}
