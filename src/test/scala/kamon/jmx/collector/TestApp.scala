package kamon.jmx.collector

import akka.actor.ActorSystem
import kamon.Kamon
import kamon.prometheus.PrometheusReporter

/*
Expected results:

# TYPE jmx_os_memory_ObjectPendingFinalizationCount histogram
jmx_os_memory_ObjectPendingFinalizationCount_bucket{le="10.0"} 21.0
jmx_os_memory_ObjectPendingFinalizationCount_bucket{le="30.0"} 21.0
jmx_os_memory_ObjectPendingFinalizationCount_bucket{le="100.0"} 21.0
jmx_os_memory_ObjectPendingFinalizationCount_bucket{le="300.0"} 21.0
jmx_os_memory_ObjectPendingFinalizationCount_bucket{le="1000.0"} 21.0
jmx_os_memory_ObjectPendingFinalizationCount_bucket{le="3000.0"} 21.0
jmx_os_memory_ObjectPendingFinalizationCount_bucket{le="10000.0"} 21.0
jmx_os_memory_ObjectPendingFinalizationCount_bucket{le="30000.0"} 21.0
jmx_os_memory_ObjectPendingFinalizationCount_bucket{le="100000.0"} 21.0
jmx_os_memory_ObjectPendingFinalizationCount_bucket{le="+Inf"} 21.0
jmx_os_memory_ObjectPendingFinalizationCount_count 21.0
jmx_os_memory_ObjectPendingFinalizationCount_sum 0.0
# TYPE jmx_os_memory_HeapMemoryUsage_max histogram
jmx_os_memory_HeapMemoryUsage_max_bucket{le="10.0"} 0.0
jmx_os_memory_HeapMemoryUsage_max_bucket{le="30.0"} 0.0
jmx_os_memory_HeapMemoryUsage_max_bucket{le="100.0"} 0.0
jmx_os_memory_HeapMemoryUsage_max_bucket{le="300.0"} 0.0
jmx_os_memory_HeapMemoryUsage_max_bucket{le="1000.0"} 0.0
jmx_os_memory_HeapMemoryUsage_max_bucket{le="3000.0"} 0.0
jmx_os_memory_HeapMemoryUsage_max_bucket{le="10000.0"} 0.0
jmx_os_memory_HeapMemoryUsage_max_bucket{le="30000.0"} 0.0
jmx_os_memory_HeapMemoryUsage_max_bucket{le="100000.0"} 0.0
jmx_os_memory_HeapMemoryUsage_max_bucket{le="+Inf"} 21.0
jmx_os_memory_HeapMemoryUsage_max_count 21.0
jmx_os_memory_HeapMemoryUsage_max_sum 79976988672.0
# TYPE jmx_os_memory_HeapMemoryUsage_committed histogram
jmx_os_memory_HeapMemoryUsage_committed_bucket{le="10.0"} 0.0
jmx_os_memory_HeapMemoryUsage_committed_bucket{le="30.0"} 0.0
jmx_os_memory_HeapMemoryUsage_committed_bucket{le="100.0"} 0.0
jmx_os_memory_HeapMemoryUsage_committed_bucket{le="300.0"} 0.0
jmx_os_memory_HeapMemoryUsage_committed_bucket{le="1000.0"} 0.0
jmx_os_memory_HeapMemoryUsage_committed_bucket{le="3000.0"} 0.0
jmx_os_memory_HeapMemoryUsage_committed_bucket{le="10000.0"} 0.0
jmx_os_memory_HeapMemoryUsage_committed_bucket{le="30000.0"} 0.0
jmx_os_memory_HeapMemoryUsage_committed_bucket{le="100000.0"} 0.0
jmx_os_memory_HeapMemoryUsage_committed_bucket{le="+Inf"} 21.0
jmx_os_memory_HeapMemoryUsage_committed_count 21.0
jmx_os_memory_HeapMemoryUsage_committed_sum 4052746240.0
# TYPE jmx_os_mbean_AvailableProcessors histogram
jmx_os_mbean_AvailableProcessors_bucket{le="10.0"} 21.0
jmx_os_mbean_AvailableProcessors_bucket{le="30.0"} 21.0
jmx_os_mbean_AvailableProcessors_bucket{le="100.0"} 21.0
jmx_os_mbean_AvailableProcessors_bucket{le="300.0"} 21.0
jmx_os_mbean_AvailableProcessors_bucket{le="1000.0"} 21.0
jmx_os_mbean_AvailableProcessors_bucket{le="3000.0"} 21.0
jmx_os_mbean_AvailableProcessors_bucket{le="10000.0"} 21.0
jmx_os_mbean_AvailableProcessors_bucket{le="30000.0"} 21.0
jmx_os_mbean_AvailableProcessors_bucket{le="100000.0"} 21.0
jmx_os_mbean_AvailableProcessors_bucket{le="+Inf"} 21.0
jmx_os_mbean_AvailableProcessors_count 21.0
jmx_os_mbean_AvailableProcessors_sum 168.0

 */

object TestApp extends App {

  implicit val system = ActorSystem()

  Kamon.addReporter(new PrometheusReporter())
  KamonJmxMetricCollector()
}
