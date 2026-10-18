package gregtech.neo.material;

public record GTMaterialPrefix(
    String field,
    String id,
    String category,
    long amount,
    String amount_expression
) {
    public String itemPath() {
        return "meta_" + GTMaterial.slug(field.isBlank() ? id : field);
    }

    public String prettyCategory() {
        return category;
    }
}
