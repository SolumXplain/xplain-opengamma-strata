/**
 * Copyright (C) 2014 - present by OpenGamma Inc. and the OpenGamma group of companies
 * 
 * Please see distribution for license.
 */
package com.opengamma.strata.basics.index;

import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;

import org.joda.beans.Bean;
import org.joda.beans.BeanDefinition;
import org.joda.beans.ImmutableBean;
import org.joda.beans.JodaBeanUtils;
import org.joda.beans.MetaProperty;
import org.joda.beans.Property;
import org.joda.beans.PropertyDefinition;
import org.joda.beans.impl.direct.DirectFieldsBeanBuilder;
import org.joda.beans.impl.direct.DirectMetaBean;
import org.joda.beans.impl.direct.DirectMetaProperty;
import org.joda.beans.impl.direct.DirectMetaPropertyMap;

import com.opengamma.strata.basics.currency.Currency;
import com.opengamma.strata.basics.date.DayCount;
import com.opengamma.strata.basics.date.DaysAdjustment;
import com.opengamma.strata.basics.date.HolidayCalendar;
import com.opengamma.strata.basics.date.HolidayCalendarId;
import com.opengamma.strata.basics.date.HolidayCalendarIds;
import com.opengamma.strata.basics.date.Tenor;
import com.opengamma.strata.basics.date.TenorAdjustment;
import com.opengamma.strata.basics.market.ReferenceData;

/**
 * An Ibor index implementation based on an immutable set of rules.
 * <p>
 * A standard immutable implementation of {@link IborIndex} that defines the currency
 * and the rules for converting from fixing to effective and maturity.
 * <p>
 * In most cases, applications should refer to indices by name, using {@link IborIndex#of(String)}.
 * The named index will typically be resolved to an instance of this class.
 * As such, it is recommended to use the {@code IborIndex} interface in application
 * code rather than directly referring to this class.
 */
@BeanDefinition
public final class ImmutableIborIndex
    implements IborIndex, ImmutableBean, Serializable {

  /**
   * The identifier, such as 'GBP-LIBOR-3M'.
   */
  @PropertyDefinition(validate = "notNull", overrideGet = true)
  private final IborIndexId id;
  /**
   * The currency of the index.
   */
  @PropertyDefinition(validate = "notNull", overrideGet = true)
  private final Currency currency;
  /**
   * The calendar that determines which dates are fixing dates.
   * <p>
   * The fixing date is when the rate is determined.
   */
  @PropertyDefinition(validate = "notNull", overrideGet = true)
  private final HolidayCalendarId fixingCalendar;
  /**
   * The fixing time.
   * <p>
   * The rate is fixed at the fixing time of the fixing date. 
   */
  @PropertyDefinition(validate = "notNull")
  private final LocalTime fixingTime;
  /**
  * The fixing time-zone.
  * <p>
  * The time-zone of the fixing time.
  */
  @PropertyDefinition(validate = "notNull")
  private final ZoneId fixingZone;
  /**
   * The adjustment applied to the effective date to obtain the fixing date.
   * <p>
   * The fixing date is the date on which the index is to be observed.
   * In most cases, the fixing date is 0 or 2 days before the effective date.
   * This data structure allows the complex rules of some indices to be represented.
   */
  @PropertyDefinition(validate = "notNull", overrideGet = true)
  private final DaysAdjustment fixingDateOffset;
  /**
   * The adjustment applied to the fixing date to obtain the effective date.
   * <p>
   * The effective date is the start date of the indexed deposit.
   * In most cases, the effective date is 0 or 2 days after the fixing date.
   * This data structure allows the complex rules of some indices to be represented.
   */
  @PropertyDefinition(validate = "notNull", overrideGet = true)
  private final DaysAdjustment effectiveDateOffset;
  /**
   * The adjustment applied to the effective date to obtain the maturity date.
   * <p>
   * The maturity date is the end date of the indexed deposit and is relative to the effective date.
   * This data structure allows the complex rules of some indices to be represented.
   */
  @PropertyDefinition(validate = "notNull", overrideGet = true)
  private final TenorAdjustment maturityDateOffset;
  /**
   * The day count convention.
   */
  @PropertyDefinition(validate = "notNull", overrideGet = true)
  private final DayCount dayCount;

  //-------------------------------------------------------------------------
  /**
   * Gets the tenor of the index.
   * 
   * @return the tenor
   */
  @Override
  public Tenor getTenor() {
    return maturityDateOffset.getTenor();
  }

  //-------------------------------------------------------------------------
  @Override
  public ZonedDateTime calculateFixingDateTime(LocalDate fixingDate) {
    return fixingDate.atTime(fixingTime).atZone(fixingZone);
  }

  @Override
  public LocalDate calculateEffectiveFromFixing(LocalDate fixingDate, ReferenceData refData) {
    LocalDate fixingBusinessDay = fixingCalendar.resolve(refData).nextOrSame(fixingDate);
    return effectiveDateOffset.adjust(fixingBusinessDay, refData);
  }

  @Override
  public LocalDate calculateMaturityFromFixing(LocalDate fixingDate, ReferenceData refData) {
    LocalDate fixingBusinessDay = fixingCalendar.resolve(refData).nextOrSame(fixingDate);
    return maturityDateOffset.adjust(effectiveDateOffset.adjust(fixingBusinessDay, refData), refData);
  }

  @Override
  public LocalDate calculateFixingFromEffective(LocalDate effectiveDate, ReferenceData refData) {
    LocalDate effectiveBusinessDay = effectiveDateCalendar(refData).nextOrSame(effectiveDate);
    return fixingDateOffset.adjust(effectiveBusinessDay, refData);
  }

  @Override
  public LocalDate calculateMaturityFromEffective(LocalDate effectiveDate, ReferenceData refData) {
    LocalDate effectiveBusinessDay = effectiveDateCalendar(refData).nextOrSame(effectiveDate);
    return maturityDateOffset.adjust(effectiveBusinessDay, refData);
  }

  // finds the calendar of the effective date
  private HolidayCalendar effectiveDateCalendar(ReferenceData refData) {
    HolidayCalendarId cal = effectiveDateOffset.getResultCalendar();
    if (cal == HolidayCalendarIds.NO_HOLIDAYS) {
      cal = fixingCalendar;
    }
    return cal.resolve(refData);
  }

  //-------------------------------------------------------------------------
  @Override
  public boolean equals(Object obj) {
    if (obj == this) {
      return true;
    }
    if (obj instanceof ImmutableIborIndex) {
      return id.equals(((ImmutableIborIndex) obj).id);
    }
    return false;
  }

  @Override
  public int hashCode() {
    return id.hashCode();
  }

  //-------------------------------------------------------------------------
  /**
   * Returns the name of the index.
   * 
   * @return the name of the index
   */
  @Override
  public String toString() {
    return getName();
  }

  //------------------------- AUTOGENERATED START -------------------------
  ///CLOVER:OFF
  /**
   * The meta-bean for {@code ImmutableIborIndex}.
   * @return the meta-bean, not null
   */
  public static ImmutableIborIndex.Meta meta() {
    return ImmutableIborIndex.Meta.INSTANCE;
  }

  static {
    JodaBeanUtils.registerMetaBean(ImmutableIborIndex.Meta.INSTANCE);
  }

  /**
   * The serialization version id.
   */
  private static final long serialVersionUID = 1L;

  /**
   * Returns a builder used to create an instance of the bean.
   * @return the builder, not null
   */
  public static ImmutableIborIndex.Builder builder() {
    return new ImmutableIborIndex.Builder();
  }

  private ImmutableIborIndex(
      IborIndexId id,
      Currency currency,
      HolidayCalendarId fixingCalendar,
      LocalTime fixingTime,
      ZoneId fixingZone,
      DaysAdjustment fixingDateOffset,
      DaysAdjustment effectiveDateOffset,
      TenorAdjustment maturityDateOffset,
      DayCount dayCount) {
    JodaBeanUtils.notNull(id, "id");
    JodaBeanUtils.notNull(currency, "currency");
    JodaBeanUtils.notNull(fixingCalendar, "fixingCalendar");
    JodaBeanUtils.notNull(fixingTime, "fixingTime");
    JodaBeanUtils.notNull(fixingZone, "fixingZone");
    JodaBeanUtils.notNull(fixingDateOffset, "fixingDateOffset");
    JodaBeanUtils.notNull(effectiveDateOffset, "effectiveDateOffset");
    JodaBeanUtils.notNull(maturityDateOffset, "maturityDateOffset");
    JodaBeanUtils.notNull(dayCount, "dayCount");
    this.id = id;
    this.currency = currency;
    this.fixingCalendar = fixingCalendar;
    this.fixingTime = fixingTime;
    this.fixingZone = fixingZone;
    this.fixingDateOffset = fixingDateOffset;
    this.effectiveDateOffset = effectiveDateOffset;
    this.maturityDateOffset = maturityDateOffset;
    this.dayCount = dayCount;
  }

  @Override
  public ImmutableIborIndex.Meta metaBean() {
    return ImmutableIborIndex.Meta.INSTANCE;
  }

  @Override
  public <R> Property<R> property(String propertyName) {
    return metaBean().<R>metaProperty(propertyName).createProperty(this);
  }

  @Override
  public Set<String> propertyNames() {
    return metaBean().metaPropertyMap().keySet();
  }

  //-----------------------------------------------------------------------
  /**
   * Gets the identifier, such as 'GBP-LIBOR-3M'.
   * @return the value of the property, not null
   */
  @Override
  public IborIndexId getId() {
    return id;
  }

  //-----------------------------------------------------------------------
  /**
   * Gets the currency of the index.
   * @return the value of the property, not null
   */
  @Override
  public Currency getCurrency() {
    return currency;
  }

  //-----------------------------------------------------------------------
  /**
   * Gets the calendar that determines which dates are fixing dates.
   * <p>
   * The fixing date is when the rate is determined.
   * @return the value of the property, not null
   */
  @Override
  public HolidayCalendarId getFixingCalendar() {
    return fixingCalendar;
  }

  //-----------------------------------------------------------------------
  /**
   * Gets the fixing time.
   * <p>
   * The rate is fixed at the fixing time of the fixing date.
   * @return the value of the property, not null
   */
  public LocalTime getFixingTime() {
    return fixingTime;
  }

  //-----------------------------------------------------------------------
  /**
   * Gets the fixing time-zone.
   * <p>
   * The time-zone of the fixing time.
   * @return the value of the property, not null
   */
  public ZoneId getFixingZone() {
    return fixingZone;
  }

  //-----------------------------------------------------------------------
  /**
   * Gets the adjustment applied to the effective date to obtain the fixing date.
   * <p>
   * The fixing date is the date on which the index is to be observed.
   * In most cases, the fixing date is 0 or 2 days before the effective date.
   * This data structure allows the complex rules of some indices to be represented.
   * @return the value of the property, not null
   */
  @Override
  public DaysAdjustment getFixingDateOffset() {
    return fixingDateOffset;
  }

  //-----------------------------------------------------------------------
  /**
   * Gets the adjustment applied to the fixing date to obtain the effective date.
   * <p>
   * The effective date is the start date of the indexed deposit.
   * In most cases, the effective date is 0 or 2 days after the fixing date.
   * This data structure allows the complex rules of some indices to be represented.
   * @return the value of the property, not null
   */
  @Override
  public DaysAdjustment getEffectiveDateOffset() {
    return effectiveDateOffset;
  }

  //-----------------------------------------------------------------------
  /**
   * Gets the adjustment applied to the effective date to obtain the maturity date.
   * <p>
   * The maturity date is the end date of the indexed deposit and is relative to the effective date.
   * This data structure allows the complex rules of some indices to be represented.
   * @return the value of the property, not null
   */
  @Override
  public TenorAdjustment getMaturityDateOffset() {
    return maturityDateOffset;
  }

  //-----------------------------------------------------------------------
  /**
   * Gets the day count convention.
   * @return the value of the property, not null
   */
  @Override
  public DayCount getDayCount() {
    return dayCount;
  }

  //-----------------------------------------------------------------------
  /**
   * Returns a builder that allows this bean to be mutated.
   * @return the mutable builder, not null
   */
  public Builder toBuilder() {
    return new Builder(this);
  }

  //-----------------------------------------------------------------------
  /**
   * The meta-bean for {@code ImmutableIborIndex}.
   */
  public static final class Meta extends DirectMetaBean {
    /**
     * The singleton instance of the meta-bean.
     */
    static final Meta INSTANCE = new Meta();

    /**
     * The meta-property for the {@code id} property.
     */
    private final MetaProperty<IborIndexId> id = DirectMetaProperty.ofImmutable(
        this, "id", ImmutableIborIndex.class, IborIndexId.class);
    /**
     * The meta-property for the {@code currency} property.
     */
    private final MetaProperty<Currency> currency = DirectMetaProperty.ofImmutable(
        this, "currency", ImmutableIborIndex.class, Currency.class);
    /**
     * The meta-property for the {@code fixingCalendar} property.
     */
    private final MetaProperty<HolidayCalendarId> fixingCalendar = DirectMetaProperty.ofImmutable(
        this, "fixingCalendar", ImmutableIborIndex.class, HolidayCalendarId.class);
    /**
     * The meta-property for the {@code fixingTime} property.
     */
    private final MetaProperty<LocalTime> fixingTime = DirectMetaProperty.ofImmutable(
        this, "fixingTime", ImmutableIborIndex.class, LocalTime.class);
    /**
     * The meta-property for the {@code fixingZone} property.
     */
    private final MetaProperty<ZoneId> fixingZone = DirectMetaProperty.ofImmutable(
        this, "fixingZone", ImmutableIborIndex.class, ZoneId.class);
    /**
     * The meta-property for the {@code fixingDateOffset} property.
     */
    private final MetaProperty<DaysAdjustment> fixingDateOffset = DirectMetaProperty.ofImmutable(
        this, "fixingDateOffset", ImmutableIborIndex.class, DaysAdjustment.class);
    /**
     * The meta-property for the {@code effectiveDateOffset} property.
     */
    private final MetaProperty<DaysAdjustment> effectiveDateOffset = DirectMetaProperty.ofImmutable(
        this, "effectiveDateOffset", ImmutableIborIndex.class, DaysAdjustment.class);
    /**
     * The meta-property for the {@code maturityDateOffset} property.
     */
    private final MetaProperty<TenorAdjustment> maturityDateOffset = DirectMetaProperty.ofImmutable(
        this, "maturityDateOffset", ImmutableIborIndex.class, TenorAdjustment.class);
    /**
     * The meta-property for the {@code dayCount} property.
     */
    private final MetaProperty<DayCount> dayCount = DirectMetaProperty.ofImmutable(
        this, "dayCount", ImmutableIborIndex.class, DayCount.class);
    /**
     * The meta-properties.
     */
    private final Map<String, MetaProperty<?>> metaPropertyMap$ = new DirectMetaPropertyMap(
        this, null,
        "id",
        "currency",
        "fixingCalendar",
        "fixingTime",
        "fixingZone",
        "fixingDateOffset",
        "effectiveDateOffset",
        "maturityDateOffset",
        "dayCount");

    /**
     * Restricted constructor.
     */
    private Meta() {
    }

    @Override
    protected MetaProperty<?> metaPropertyGet(String propertyName) {
      switch (propertyName.hashCode()) {
        case 3355:  // id
          return id;
        case 575402001:  // currency
          return currency;
        case 394230283:  // fixingCalendar
          return fixingCalendar;
        case 1255686170:  // fixingTime
          return fixingTime;
        case 1255870713:  // fixingZone
          return fixingZone;
        case 873743726:  // fixingDateOffset
          return fixingDateOffset;
        case 1571923688:  // effectiveDateOffset
          return effectiveDateOffset;
        case 1574797394:  // maturityDateOffset
          return maturityDateOffset;
        case 1905311443:  // dayCount
          return dayCount;
      }
      return super.metaPropertyGet(propertyName);
    }

    @Override
    public ImmutableIborIndex.Builder builder() {
      return new ImmutableIborIndex.Builder();
    }

    @Override
    public Class<? extends ImmutableIborIndex> beanType() {
      return ImmutableIborIndex.class;
    }

    @Override
    public Map<String, MetaProperty<?>> metaPropertyMap() {
      return metaPropertyMap$;
    }

    //-----------------------------------------------------------------------
    /**
     * The meta-property for the {@code id} property.
     * @return the meta-property, not null
     */
    public MetaProperty<IborIndexId> id() {
      return id;
    }

    /**
     * The meta-property for the {@code currency} property.
     * @return the meta-property, not null
     */
    public MetaProperty<Currency> currency() {
      return currency;
    }

    /**
     * The meta-property for the {@code fixingCalendar} property.
     * @return the meta-property, not null
     */
    public MetaProperty<HolidayCalendarId> fixingCalendar() {
      return fixingCalendar;
    }

    /**
     * The meta-property for the {@code fixingTime} property.
     * @return the meta-property, not null
     */
    public MetaProperty<LocalTime> fixingTime() {
      return fixingTime;
    }

    /**
     * The meta-property for the {@code fixingZone} property.
     * @return the meta-property, not null
     */
    public MetaProperty<ZoneId> fixingZone() {
      return fixingZone;
    }

    /**
     * The meta-property for the {@code fixingDateOffset} property.
     * @return the meta-property, not null
     */
    public MetaProperty<DaysAdjustment> fixingDateOffset() {
      return fixingDateOffset;
    }

    /**
     * The meta-property for the {@code effectiveDateOffset} property.
     * @return the meta-property, not null
     */
    public MetaProperty<DaysAdjustment> effectiveDateOffset() {
      return effectiveDateOffset;
    }

    /**
     * The meta-property for the {@code maturityDateOffset} property.
     * @return the meta-property, not null
     */
    public MetaProperty<TenorAdjustment> maturityDateOffset() {
      return maturityDateOffset;
    }

    /**
     * The meta-property for the {@code dayCount} property.
     * @return the meta-property, not null
     */
    public MetaProperty<DayCount> dayCount() {
      return dayCount;
    }

    //-----------------------------------------------------------------------
    @Override
    protected Object propertyGet(Bean bean, String propertyName, boolean quiet) {
      switch (propertyName.hashCode()) {
        case 3355:  // id
          return ((ImmutableIborIndex) bean).getId();
        case 575402001:  // currency
          return ((ImmutableIborIndex) bean).getCurrency();
        case 394230283:  // fixingCalendar
          return ((ImmutableIborIndex) bean).getFixingCalendar();
        case 1255686170:  // fixingTime
          return ((ImmutableIborIndex) bean).getFixingTime();
        case 1255870713:  // fixingZone
          return ((ImmutableIborIndex) bean).getFixingZone();
        case 873743726:  // fixingDateOffset
          return ((ImmutableIborIndex) bean).getFixingDateOffset();
        case 1571923688:  // effectiveDateOffset
          return ((ImmutableIborIndex) bean).getEffectiveDateOffset();
        case 1574797394:  // maturityDateOffset
          return ((ImmutableIborIndex) bean).getMaturityDateOffset();
        case 1905311443:  // dayCount
          return ((ImmutableIborIndex) bean).getDayCount();
      }
      return super.propertyGet(bean, propertyName, quiet);
    }

    @Override
    protected void propertySet(Bean bean, String propertyName, Object newValue, boolean quiet) {
      metaProperty(propertyName);
      if (quiet) {
        return;
      }
      throw new UnsupportedOperationException("Property cannot be written: " + propertyName);
    }

  }

  //-----------------------------------------------------------------------
  /**
   * The bean-builder for {@code ImmutableIborIndex}.
   */
  public static final class Builder extends DirectFieldsBeanBuilder<ImmutableIborIndex> {

    private IborIndexId id;
    private Currency currency;
    private HolidayCalendarId fixingCalendar;
    private LocalTime fixingTime;
    private ZoneId fixingZone;
    private DaysAdjustment fixingDateOffset;
    private DaysAdjustment effectiveDateOffset;
    private TenorAdjustment maturityDateOffset;
    private DayCount dayCount;

    /**
     * Restricted constructor.
     */
    private Builder() {
    }

    /**
     * Restricted copy constructor.
     * @param beanToCopy  the bean to copy from, not null
     */
    private Builder(ImmutableIborIndex beanToCopy) {
      this.id = beanToCopy.getId();
      this.currency = beanToCopy.getCurrency();
      this.fixingCalendar = beanToCopy.getFixingCalendar();
      this.fixingTime = beanToCopy.getFixingTime();
      this.fixingZone = beanToCopy.getFixingZone();
      this.fixingDateOffset = beanToCopy.getFixingDateOffset();
      this.effectiveDateOffset = beanToCopy.getEffectiveDateOffset();
      this.maturityDateOffset = beanToCopy.getMaturityDateOffset();
      this.dayCount = beanToCopy.getDayCount();
    }

    //-----------------------------------------------------------------------
    @Override
    public Object get(String propertyName) {
      switch (propertyName.hashCode()) {
        case 3355:  // id
          return id;
        case 575402001:  // currency
          return currency;
        case 394230283:  // fixingCalendar
          return fixingCalendar;
        case 1255686170:  // fixingTime
          return fixingTime;
        case 1255870713:  // fixingZone
          return fixingZone;
        case 873743726:  // fixingDateOffset
          return fixingDateOffset;
        case 1571923688:  // effectiveDateOffset
          return effectiveDateOffset;
        case 1574797394:  // maturityDateOffset
          return maturityDateOffset;
        case 1905311443:  // dayCount
          return dayCount;
        default:
          throw new NoSuchElementException("Unknown property: " + propertyName);
      }
    }

    @Override
    public Builder set(String propertyName, Object newValue) {
      switch (propertyName.hashCode()) {
        case 3355:  // id
          this.id = (IborIndexId) newValue;
          break;
        case 575402001:  // currency
          this.currency = (Currency) newValue;
          break;
        case 394230283:  // fixingCalendar
          this.fixingCalendar = (HolidayCalendarId) newValue;
          break;
        case 1255686170:  // fixingTime
          this.fixingTime = (LocalTime) newValue;
          break;
        case 1255870713:  // fixingZone
          this.fixingZone = (ZoneId) newValue;
          break;
        case 873743726:  // fixingDateOffset
          this.fixingDateOffset = (DaysAdjustment) newValue;
          break;
        case 1571923688:  // effectiveDateOffset
          this.effectiveDateOffset = (DaysAdjustment) newValue;
          break;
        case 1574797394:  // maturityDateOffset
          this.maturityDateOffset = (TenorAdjustment) newValue;
          break;
        case 1905311443:  // dayCount
          this.dayCount = (DayCount) newValue;
          break;
        default:
          throw new NoSuchElementException("Unknown property: " + propertyName);
      }
      return this;
    }

    @Override
    public Builder set(MetaProperty<?> property, Object value) {
      super.set(property, value);
      return this;
    }

    @Override
    public Builder setString(String propertyName, String value) {
      setString(meta().metaProperty(propertyName), value);
      return this;
    }

    @Override
    public Builder setString(MetaProperty<?> property, String value) {
      super.setString(property, value);
      return this;
    }

    @Override
    public Builder setAll(Map<String, ? extends Object> propertyValueMap) {
      super.setAll(propertyValueMap);
      return this;
    }

    @Override
    public ImmutableIborIndex build() {
      return new ImmutableIborIndex(
          id,
          currency,
          fixingCalendar,
          fixingTime,
          fixingZone,
          fixingDateOffset,
          effectiveDateOffset,
          maturityDateOffset,
          dayCount);
    }

    //-----------------------------------------------------------------------
    /**
     * Sets the identifier, such as 'GBP-LIBOR-3M'.
     * @param id  the new value, not null
     * @return this, for chaining, not null
     */
    public Builder id(IborIndexId id) {
      JodaBeanUtils.notNull(id, "id");
      this.id = id;
      return this;
    }

    /**
     * Sets the currency of the index.
     * @param currency  the new value, not null
     * @return this, for chaining, not null
     */
    public Builder currency(Currency currency) {
      JodaBeanUtils.notNull(currency, "currency");
      this.currency = currency;
      return this;
    }

    /**
     * Sets the calendar that determines which dates are fixing dates.
     * <p>
     * The fixing date is when the rate is determined.
     * @param fixingCalendar  the new value, not null
     * @return this, for chaining, not null
     */
    public Builder fixingCalendar(HolidayCalendarId fixingCalendar) {
      JodaBeanUtils.notNull(fixingCalendar, "fixingCalendar");
      this.fixingCalendar = fixingCalendar;
      return this;
    }

    /**
     * Sets the fixing time.
     * <p>
     * The rate is fixed at the fixing time of the fixing date.
     * @param fixingTime  the new value, not null
     * @return this, for chaining, not null
     */
    public Builder fixingTime(LocalTime fixingTime) {
      JodaBeanUtils.notNull(fixingTime, "fixingTime");
      this.fixingTime = fixingTime;
      return this;
    }

    /**
     * Sets the fixing time-zone.
     * <p>
     * The time-zone of the fixing time.
     * @param fixingZone  the new value, not null
     * @return this, for chaining, not null
     */
    public Builder fixingZone(ZoneId fixingZone) {
      JodaBeanUtils.notNull(fixingZone, "fixingZone");
      this.fixingZone = fixingZone;
      return this;
    }

    /**
     * Sets the adjustment applied to the effective date to obtain the fixing date.
     * <p>
     * The fixing date is the date on which the index is to be observed.
     * In most cases, the fixing date is 0 or 2 days before the effective date.
     * This data structure allows the complex rules of some indices to be represented.
     * @param fixingDateOffset  the new value, not null
     * @return this, for chaining, not null
     */
    public Builder fixingDateOffset(DaysAdjustment fixingDateOffset) {
      JodaBeanUtils.notNull(fixingDateOffset, "fixingDateOffset");
      this.fixingDateOffset = fixingDateOffset;
      return this;
    }

    /**
     * Sets the adjustment applied to the fixing date to obtain the effective date.
     * <p>
     * The effective date is the start date of the indexed deposit.
     * In most cases, the effective date is 0 or 2 days after the fixing date.
     * This data structure allows the complex rules of some indices to be represented.
     * @param effectiveDateOffset  the new value, not null
     * @return this, for chaining, not null
     */
    public Builder effectiveDateOffset(DaysAdjustment effectiveDateOffset) {
      JodaBeanUtils.notNull(effectiveDateOffset, "effectiveDateOffset");
      this.effectiveDateOffset = effectiveDateOffset;
      return this;
    }

    /**
     * Sets the adjustment applied to the effective date to obtain the maturity date.
     * <p>
     * The maturity date is the end date of the indexed deposit and is relative to the effective date.
     * This data structure allows the complex rules of some indices to be represented.
     * @param maturityDateOffset  the new value, not null
     * @return this, for chaining, not null
     */
    public Builder maturityDateOffset(TenorAdjustment maturityDateOffset) {
      JodaBeanUtils.notNull(maturityDateOffset, "maturityDateOffset");
      this.maturityDateOffset = maturityDateOffset;
      return this;
    }

    /**
     * Sets the day count convention.
     * @param dayCount  the new value, not null
     * @return this, for chaining, not null
     */
    public Builder dayCount(DayCount dayCount) {
      JodaBeanUtils.notNull(dayCount, "dayCount");
      this.dayCount = dayCount;
      return this;
    }

    //-----------------------------------------------------------------------
    @Override
    public String toString() {
      StringBuilder buf = new StringBuilder(320);
      buf.append("ImmutableIborIndex.Builder{");
      buf.append("id").append('=').append(JodaBeanUtils.toString(id)).append(',').append(' ');
      buf.append("currency").append('=').append(JodaBeanUtils.toString(currency)).append(',').append(' ');
      buf.append("fixingCalendar").append('=').append(JodaBeanUtils.toString(fixingCalendar)).append(',').append(' ');
      buf.append("fixingTime").append('=').append(JodaBeanUtils.toString(fixingTime)).append(',').append(' ');
      buf.append("fixingZone").append('=').append(JodaBeanUtils.toString(fixingZone)).append(',').append(' ');
      buf.append("fixingDateOffset").append('=').append(JodaBeanUtils.toString(fixingDateOffset)).append(',').append(' ');
      buf.append("effectiveDateOffset").append('=').append(JodaBeanUtils.toString(effectiveDateOffset)).append(',').append(' ');
      buf.append("maturityDateOffset").append('=').append(JodaBeanUtils.toString(maturityDateOffset)).append(',').append(' ');
      buf.append("dayCount").append('=').append(JodaBeanUtils.toString(dayCount));
      buf.append('}');
      return buf.toString();
    }

  }

  ///CLOVER:ON
  //-------------------------- AUTOGENERATED END --------------------------
}
