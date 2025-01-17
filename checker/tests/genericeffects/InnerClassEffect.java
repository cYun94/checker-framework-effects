import org.checkerframework.checker.genericeffects.qual.DecimalOverflow;
import org.checkerframework.checker.genericeffects.qual.DecimalPrecisionLoss;
import org.checkerframework.checker.genericeffects.qual.DefaultEffect;
import org.checkerframework.checker.genericeffects.qual.IntegerOverflow;
import org.checkerframework.checker.genericeffects.qual.IntegerPrecisionLoss;
import org.checkerframework.checker.genericeffects.qual.NumberOverflow;

public class InnerClassEffect {

  public void safeCast() {
    // :: error: (operation.invalid)
    integerOverflowEffect();
    // :: error: (operation.invalid)
    integerPrecisionLossEffect();
    // :: error: (operation.invalid)
    decimalOverflowEffect();
    // :: error: (operation.invalid)
    decimalPrecisionLossEffect();
  }

  @IntegerOverflow
  public void integerOverflowEffect() {}

  @IntegerPrecisionLoss
  public void integerPrecisionLossEffect() {}

  @DecimalOverflow
  public void decimalOverflowEffect() {}

  @DecimalPrecisionLoss
  public void decimalPrecisionLossEffect() {}

  @DefaultEffect(NumberOverflow.class)
  public class UnsafeClass {

    public void numberOverflow() {
      // okay
      integerOverflowEffect();
      // :: error: (operation.invalid)
      integerPrecisionLossEffect();
      // okay
      decimalOverflowEffect();
      // :: error: (operation.invalid)
      decimalPrecisionLossEffect();
    }

    @IntegerOverflow
    public void integerOverflowEffect() {}

    @IntegerPrecisionLoss
    public void integerPrecisionLossEffect() {}

    @DecimalOverflow
    public void decimalOverflowEffect() {}

    @DecimalPrecisionLoss
    public void decimalPrecisionLossEffect() {}
  }
}
