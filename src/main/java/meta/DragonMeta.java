package meta;

public class DragonMeta {

    private String name;
    private int id, elementId, a1Max, a2Max, rarity;
    private boolean has5UB;

    public DragonMeta(String name, int id, int elementId, int a1Max, int a2Max,
                          int rarity, boolean has5UB){
        this.name = name;
        this.id = id;
        this.elementId = elementId;
        this.a1Max = a1Max;
        this.a2Max = a2Max;
        this.rarity = rarity;
        this.has5UB = has5UB;
    }

    public String getName(){ return name; }
    public int getId(){ return id; }
    public int getElementId(){ return elementId; }
    public int getA1Max(){ return a1Max; }
    public int getA2Max(){ return a2Max; }
    public int getRarity(){ return rarity; }
    public boolean has5UB(){ return has5UB; }
}
