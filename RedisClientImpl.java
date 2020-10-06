
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.concurrent.TimeUnit;

@Service
public class RedisClientImpl implements RedisClient {

    final RedisTemplate[] redisTemplates;

    public DeliveryRedisClientImpl(RedisTemplate[] redisTemplates) {
        this.redisTemplates = redisTemplates;
    }

    @Override
    public boolean setStrWithVKey(String key, String val,Integer ttl) {
        ValueOperations<String,String> ops = getRedisClient(key).opsForValue();
        ops.set(key,val,ttl, TimeUnit.MILLISECONDS);
        ops.set(key+"_v",val,ttl*2, TimeUnit.MILLISECONDS); // 자동 만료시 처리할 데이타가 _v 에 따로 저장된다. (자동만료시 key 의 데이타는 사라지므로 여기서 값을 꺼내야함)

        return true;
    }

    @Override
    public String getStr(String key) {

        ValueOperations<String,String> ops = getRedisClient(key).opsForValue();
        return ops.get(key);
    }

    @Override
    public void delete(String key) {
        // XXX redistemplate 2.1 부터 unlink 을 지원한다. orz. 버전업을 하면 unlink 수정하고 테스트 해라.
        getRedisClient(key).delete(key);
    }

    private RedisTemplate getRedisClient(String recvKey) {

        // key 값이 _v 일수도 있다. 순수하게 redisClient 를 찾기위해서 실제 키만 사용한다. (tz_키 or tz_키_v)
        String key = recvKey.split("_")[1];

        RedisTemplate redisTemplate = null;
        Integer lastNum = Integer.parseInt(key.substring(key.length()-1,key.length()));

        Integer serverCnt = redisTemplates.length;

        for (int i = 0; i < serverCnt; i++) {
            if( lastNum < (10/ serverCnt)*(i+1)) {
                redisTemplate = redisTemplates[i];
                log().info("redis number [{}]",i);
                break;
            } else if(serverCnt == (i+1)) {
                redisTemplate = redisTemplates[i];
                log().info("else.. redis number [{}]",i);
                break;
            }
        }

        return redisTemplate;
    }


    @Override
    public boolean setStr(String key, String val,int ttl) {
        RedisTemplate redisTemplate = getRedisClient(key);
        ValueOperations<String,String> ops = redisTemplate.opsForValue();
        ops.set(key,val,ttl,TimeUnit.MILLISECONDS);
        return true;
    }
}
