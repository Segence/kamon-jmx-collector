package kamon.jmx.collector

import akka.actor.ActorSystem
import kamon.Kamon
import kamon.prometheus.PrometheusReporter

/*
Expected results:

# TYPE jmx_os_memory_ObjectPendingFinalizationCount histogram
jmx_os_memory_ObjectPendingFinalizationCount_bucket{le="10.0"} 17.0
jmx_os_memory_ObjectPendingFinalizationCount_bucket{le="30.0"} 17.0
jmx_os_memory_ObjectPendingFinalizationCount_bucket{le="100.0"} 17.0
jmx_os_memory_ObjectPendingFinalizationCount_bucket{le="300.0"} 17.0
jmx_os_memory_ObjectPendingFinalizationCount_bucket{le="1000.0"} 17.0
jmx_os_memory_ObjectPendingFinalizationCount_bucket{le="3000.0"} 17.0
jmx_os_memory_ObjectPendingFinalizationCount_bucket{le="10000.0"} 17.0
jmx_os_memory_ObjectPendingFinalizationCount_bucket{le="30000.0"} 17.0
jmx_os_memory_ObjectPendingFinalizationCount_bucket{le="100000.0"} 17.0
jmx_os_memory_ObjectPendingFinalizationCount_bucket{le="+Inf"} 17.0
jmx_os_memory_ObjectPendingFinalizationCount_count 17.0
jmx_os_memory_ObjectPendingFinalizationCount_sum 0.0
# TYPE jmx_os_mbean_AvailableProcessors histogram
jmx_os_mbean_AvailableProcessors_bucket{le="10.0"} 17.0
jmx_os_mbean_AvailableProcessors_bucket{le="30.0"} 17.0
jmx_os_mbean_AvailableProcessors_bucket{le="100.0"} 17.0
jmx_os_mbean_AvailableProcessors_bucket{le="300.0"} 17.0
jmx_os_mbean_AvailableProcessors_bucket{le="1000.0"} 17.0
jmx_os_mbean_AvailableProcessors_bucket{le="3000.0"} 17.0
jmx_os_mbean_AvailableProcessors_bucket{le="10000.0"} 17.0
jmx_os_mbean_AvailableProcessors_bucket{le="30000.0"} 17.0
jmx_os_mbean_AvailableProcessors_bucket{le="100000.0"} 17.0
jmx_os_mbean_AvailableProcessors_bucket{le="+Inf"} 17.0
jmx_os_mbean_AvailableProcessors_count 17.0
jmx_os_mbean_AvailableProcessors_sum 136.0

 */

object TestApp extends App {

  implicit val system = ActorSystem()

  Kamon.addReporter(new PrometheusReporter())
  KamonJmxMetricCollector()
}
