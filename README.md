# Redis
여기로직들은 여러이유로 이름을 바꾸거나 소스 일부를 제거한 로직들입니다.
그냥 내용을 참고하는 정도로 참고하시면됩니다. 

즉 이대로 돌리면 안돌아갑니다.

여기소스들에서 좀 특이한점은 한 인스턴스가 여러 레디스를 바라보고 그 여러레디스로부터 만료이벤트를 받는 구조입니다. 단독 레디스 여러대로 간단한 샤딩 흉내를 내기 위함이었습니다.

참고로 부트에서 이런형태로 만들때는 SpringRedisAutoConfiguration.. 뭐 이런 류의 클래스를 exclude 하셔야 원하는 형태의 구성이 가능해집니다.
