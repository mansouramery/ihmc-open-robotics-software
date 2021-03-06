package us.ihmc.tools.factories;

public abstract class FactoryField<T>
{
   protected boolean hasBeenSet = false;
   protected String fieldName;
   protected T fieldValue = null;
   
   public FactoryField(String fieldName)
   {
      this.fieldName = fieldName;
   }
   
   public void set(T fieldValue)
   {
      hasBeenSet = true;
      this.fieldValue = fieldValue;
   }
   
   public T get()
   {
      if (!hasBeenSet)
      {
         throw new FactoryFieldNotSetException(fieldName);
      }
      else
      {
         return fieldValue;
      }
   }
}
