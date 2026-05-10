package com.fbp.engine.runner;

import com.fbp.engine.cli.FbpCli;
import com.fbp.engine.engine.FlowManager;
import com.fbp.engine.influx.InfluxBatchWriter;
import com.fbp.engine.influx.InfluxWriterConfig;
import com.fbp.engine.metrics.MetricsCollector;
import com.fbp.engine.node.stage1.*;
import com.fbp.engine.node.stage2.*;
import com.fbp.engine.registry.NodeRegistry;

import java.io.InputStreamReader;
import java.util.Map;

/**
 * FBP 엔진 CLI 진입점.
 */
public class Main {
    public static void main(String[] args) {
        NodeRegistry registry = buildRegistry();

        InfluxWriterConfig influxConfig = buildInfluxConfig();
        InfluxBatchWriter influxWriter = new InfluxBatchWriter(influxConfig);

        MetricsCollector metricsCollector = new MetricsCollector(influxWriter);
        metricsCollector.start();

        FlowManager flowManager = new FlowManager(registry, metricsCollector);

        FbpCli cli = new FbpCli(flowManager, registry, metricsCollector, influxWriter, influxConfig);
        try {
            cli.run(new InputStreamReader(System.in), System.out);
        } finally {
            metricsCollector.close();
            influxWriter.close();
        }
    }

    private static NodeRegistry buildRegistry() {
        NodeRegistry r = new NodeRegistry();
        r.register("TimerNode",             (id, cfg) -> new TimerNode(id, toLong(cfg, "intervalMs", 1000L)));
        r.register("FilterNode",            (id, cfg) -> new FilterNode(id, str(cfg, "key", "value"), toDouble(cfg, "threshold", 0.0)));
        r.register("ThresholdFilterNode",   (id, cfg) -> new ThresholdFilterNode(id, str(cfg, "fieldName", "value"), toDouble(cfg, "threshold", 0.0)));
        r.register("PrintNode",             (id, cfg) -> new PrintNode(id));
        r.register("LogNode",               (id, cfg) -> new LogNode(id));
        r.register("TransformNode",         (id, cfg) -> new TransformNode(id, m -> m)); // identity
        r.register("AlertNode",             (id, cfg) -> new AlertNode(id));
        r.register("FileWriterNode",        (id, cfg) -> new FileWriterNode(id, str(cfg, "filePath", "/tmp/fbp-" + id + ".log")));
        r.register("CounterNode",           (id, cfg) -> new CounterNode(id));
        r.register("DelayNode",             (id, cfg) -> new DelayNode(id, toLong(cfg, "delayMs", 100L)));
        r.register("SplitNode",             (id, cfg) -> new SplitNode(id, str(cfg, "key", "value"), toDouble(cfg, "threshold", 0.0)));
        r.register("CollectorNode",         (id, cfg) -> new CollectorNode(id));
        r.register("GeneratorNode",         (id, cfg) -> new GeneratorNode(id));
        r.register("TemperatureSensorNode", (id, cfg) -> new TemperatureSensorNode(id, toDouble(cfg, "min", 0.0),   toDouble(cfg, "max", 100.0)));
        r.register("HumiditySensorNode",     (id, cfg) -> new HumiditySensorNode(id,    toDouble(cfg, "min", 0.0),   toDouble(cfg, "max", 100.0)));
        r.register("MqttSubscriberNode",    (id, cfg) -> new MqttSubscriberNode(id, cfg));
        r.register("MqttPublisherNode",     (id, cfg) -> new MqttPublisherNode(id, cfg));
        r.register("EchoProtocolNode",      (id, cfg) -> new EchoProtocolNode(id, cfg));
        return r;
    }

    private static InfluxWriterConfig buildInfluxConfig() {
        String url    = getenv("FBP_INFLUX_URL",    "http://localhost:8086");
        String token  = getenv("FBP_INFLUX_TOKEN",  "fbp-admin-token-please-change");
        String org    = getenv("FBP_INFLUX_ORG",    "fbp");
        String bucket = getenv("FBP_INFLUX_BUCKET", "fbp-metrics");
        return InfluxWriterConfig.defaults(url, token, org, bucket);
    }

    private static String getenv(String key, String defaultValue) {
        String v = System.getenv(key);
        return (v != null && !v.isBlank()) ? v : defaultValue;
    }

    private static long toLong(Map<String, Object> cfg, String key, long def) {
        Object v = cfg.get(key);
        if (v == null) return def;
        return ((Number) v).longValue();
    }

    private static double toDouble(Map<String, Object> cfg, String key, double def) {
        Object v = cfg.get(key);
        if (v == null) return def;
        return ((Number) v).doubleValue();
    }

    private static String str(Map<String, Object> cfg, String key, String def) {
        Object v = cfg.get(key);
        return v != null ? v.toString() : def;
    }
}
