package gregtech.neo.recipe;

import com.google.gson.annotations.SerializedName;

import java.util.List;

public record GTAlloyRecipe(
    @SerializedName("id") String id,
    @SerializedName("output_material") String outputMaterial,
    @SerializedName("common_divider") int commonDivider,
    @SerializedName("components") List<Component> components,
    @SerializedName("source_file") String sourceFile,
    @SerializedName("source_line") int sourceLine,
    @SerializedName("source_method") String sourceMethod,
    @SerializedName("status") String status,
    @SerializedName("unresolved_reason") String unresolvedReason
) {
    public record Component(
        @SerializedName("expression") String expression,
        @SerializedName("material") String material,
        @SerializedName("amount_expression") String amountExpression,
        @SerializedName("amount_raw") long amountRaw,
        @SerializedName("amount_units") int amountUnits
    ) {
    }
}
