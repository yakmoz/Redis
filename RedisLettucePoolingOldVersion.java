import org.apache.commons.lang3.StringUtils;
import org.apache.commons.pool2.impl.GenericObjectPoolConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.lettuce.DefaultLettucePool;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.listener.PatternTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

@Configuration
public class RedisLettucePoolingOldVersion {

    protected final static Logger LOG = LoggerFactory.getLogger(RedisLettucePoolingOldVersion.class);

    // 원래는 ... 서버에게 살아있는지 확인하고 set/get 하게 해야하는데 어차피 redis 를 None HA , 1분안에 복구 .. 이므로 별도의 health 체크를 하지 않는다.
    Integer numOfRedisServer = Integer.parseInt(StringUtils.defaultIfEmpty(ApplicationProperties.getValue("redis.num"),"3"));

    String[] sandRedisHosts = null;
    Integer[] sandRedisPorts = null;
    String[] redisHosts = null;
    Integer[] redisPorts = null;

    Integer maxTotal = Integer.parseInt(StringUtils.defaultIfEmpty(ApplicationProperties.getValue("redis.maxtotal"),"16"));
    Integer maxIdle = Integer.parseInt(StringUtils.defaultIfEmpty(ApplicationProperties.getValue("redis.maxidle"),"16"));

    /**
     * 생성자 style
     * Redis 서버 대수를 정한다.
     */
    public DeliveryRedisConfig() {
        if(!ServerMode.isProductMode()) {
//            numOfRedisServer = 3;
            numOfRedisServer = 2;
            sandRedisHosts = new String[numOfRedisServer];
            sandRedisPorts = new Integer[numOfRedisServer];
            sandRedisHosts[0] = "192.168.0.110";
            sandRedisPorts[0] = 6379;
            sandRedisHosts[1] = "192.168.0.111";
            sandRedisPorts[1] = 6379;
//            sandRedisHosts[2] = "127.0.0.1";
//            sandRedisPorts[2] = 6379;

            redisHosts = sandRedisHosts;
            redisPorts = sandRedisPorts;
        }
    }


    @Bean
    public RedisTemplate[] redisTemplates() {

        RedisTemplate[] redisTemplates = new StringRedisTemplate[numOfRedisServer];

        for (Integer i = 0; i < numOfRedisServer; i++) {
            redisTemplates[i] = new StringRedisTemplate(lettuceConnectionFactory(redisHosts[i], redisPorts[i]));

            redisTemplates[i].setKeySerializer(new StringRedisSerializer());
            redisTemplates[i].setValueSerializer(new GenericJackson2JsonRedisSerializer()); // value to json format
        }
        return redisTemplates;
    }

    private LettuceConnectionFactory lettuceConnectionFactory(String host, int port) {
        DefaultLettucePool lettucePool = new DefaultLettucePool(host,port,getPoolConfig());
        lettucePool.afterPropertiesSet();
        LettuceConnectionFactory lettuceConnectionFactory = new LettuceConnectionFactory(lettucePool);
        lettuceConnectionFactory.afterPropertiesSet();

        // If shareNativeConnection is true, the pool will be used to select a connection for blocking and tx operations only, which should not share a connection. If native connection sharing is disabled, the selected connection will be used for all operations.
        // https://docs.spring.io/spring-data/data-redis/docs/current/api/org/springframework/data/redis/connection/lettuce/LettuceConnectionFactory.html
        // Enables multiple LettuceConnections to share a single native connection. If set to false, every operation on LettuceConnection will open and close a socket.
        // To use a dedicated connection each time, set shareNativeConnection to false.
        //lettuceConnectionFactory.setShareNativeConnection(true);
        lettuceConnectionFactory.setTimeout(250);

        return lettuceConnectionFactory;
    }

    private GenericObjectPoolConfig getPoolConfig(){
        GenericObjectPoolConfig poolConfig = new GenericObjectPoolConfig();//--> GenericObjectPoolConfig 를 대신 쓰란다. 없애버린다고. 내용을 보면 그냥 확장한거다. 문제가 있는게 아니라 존재의 의미때문인듯
        poolConfig.setMaxTotal(maxTotal);
        poolConfig.setMaxIdle(maxIdle);
        poolConfig.setMinIdle(maxIdle);

        return poolConfig;
    }

    @Autowired
    private ApplicationContext applicationContext;

    @Bean
        // subscription 이 되려면 Bean type 이 맞아야 한다. 배열로는 안됨. 해서 그냥 이 안에서 다이나믹 Bean 생성을 한다. 위치는 여기에 둔다. 나중에 찾기 편한게...
    RedisMessageListenerContainer[] keyExpirationListenerContainer(ExpirationListener expirationListener) {
        DefaultListableBeanFactory context = (DefaultListableBeanFactory) applicationContext.getAutowireCapableBeanFactory();
        RedisMessageListenerContainer[] listenerContainer = new RedisMessageListenerContainer[numOfRedisServer];

        for (int i = 0; i < numOfRedisServer ; i++) {
            listenerContainer[i] = new RedisMessageListenerContainer();
            listenerContainer[i].setConnectionFactory(lettuceConnectionFactory(redisHosts[i], redisPorts[i]));
            listenerContainer[i].addMessageListener(expirationListener, new PatternTopic("__keyevent@*__:expired"));// 이게... 아마 spring redis data 의 근래버전은 addMessageListener 에 listener 가 아니라 adaptor 를 받을 수 있을거다.
            listenerContainer[i].setErrorHandler(e -> LOG.error("There was an error in redis key expiration listener container", e));

            listenerContainer[i].afterPropertiesSet();

            context.registerSingleton("keyExpirationListenerContainer"+i,listenerContainer[i]);
        }


        return listenerContainer;
    }
}
