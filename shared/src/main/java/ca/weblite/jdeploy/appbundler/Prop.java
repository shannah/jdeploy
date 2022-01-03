package ca.weblite.jdeploy.appbundler;


public class Prop {
    private String name, value;
    public Prop(String name, String value) {
        this.name = name;
        this.value = value;
    }
    
    public String getName() { return this.name; }
    public String getValue() { return this.value; }
}
