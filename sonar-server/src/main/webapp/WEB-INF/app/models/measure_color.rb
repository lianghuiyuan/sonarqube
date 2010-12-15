#
# Sonar, entreprise quality control tool.
# Copyright (C) 2009 SonarSource SA
# mailto:contact AT sonarsource DOT com
#
# Sonar is free software; you can redistribute it and/or
# modify it under the terms of the GNU Lesser General Public
# License as published by the Free Software Foundation; either
# version 3 of the License, or (at your option) any later version.
#
# Sonar is distributed in the hope that it will be useful,
# but WITHOUT ANY WARRANTY; without even the implied warranty of
# MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
# Lesser General Public License for more details.
#
# You should have received a copy of the GNU Lesser General Public
# License along with Sonar; if not, write to the Free Software
# Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02
#
class MeasureColor

  MIN_COLOR=Color::RGB.from_html("EE0000")   # red
  MEAN_COLOR=Color::RGB.from_html("FFEE00")   # orange
  MAX_COLOR=Color::RGB.from_html("00AA00")   # green
  NONE_COLOR=Color::RGB.from_html("DDDDDD")   # gray

  #
  # Options :
  #  * min : min value, else the metric worst_value
  #  * max : max value, else the metric best_value
  #  * period_index: integer between 1 and 5 if set, else nil
  #  * check_alert_status: true|false. Default is true.
  #
  def self.color(measure, options={})
    return NONE_COLOR if measure.nil?

    max_value = options[:max] || measure.metric.best_value
    min_value = options[:min] || measure.metric.worst_value
    percent=-1.0
    
    if options[:period_index]
      if min_value && max_value
        value=measure.variation(options[:period_index])
        percent = value_to_percent(value, min_value, max_value)
      end
    else
      if !measure.alert_status.blank? && (options[:check_alert_status]||true)
        case(measure.alert_status)
          when Metric::TYPE_LEVEL_OK : percent=100.0
          when Metric::TYPE_LEVEL_ERROR : percent=0.0
          when Metric::TYPE_LEVEL_WARN : percent=50.0
        end
      elsif measure.metric.value_type==Metric::VALUE_TYPE_LEVEL
        case(measure.text_value)
          when Metric::TYPE_LEVEL_OK : percent=100.0
          when Metric::TYPE_LEVEL_WARN : percent=50.0
          when Metric::TYPE_LEVEL_ERROR : percent=0.0
        end
      elsif measure.value && max_value && min_value
        percent = value_to_percent(measure.value, min_value, max_value)
      end
    end

    if percent<0.0
      NONE_COLOR
    elsif (percent > 50.0)
      MAX_COLOR.mix_with(MEAN_COLOR, (percent - 50.0) * 2.0)
    else
      MIN_COLOR.mix_with(MEAN_COLOR, (50.0 - percent) * 2.0)
    end
  end


  def self.value_to_percent(value, min, max)
    percent = 100.0 * (value.to_f - min.to_f) / (max.to_f - min.to_f)
    percent=100.0 if percent>100.0
    percent=0.0 if percent<0.0
    percent
  end
end
