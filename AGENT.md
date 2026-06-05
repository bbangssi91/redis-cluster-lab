# Redis Cluster Lab - Agent Guide

## Project Overview

본 프로젝트는 Redis Cluster 환경에서 발생하는 분산 시스템 이슈를 직접 재현하고 검증하기 위한 실험 프로젝트이다.

Redis 사용법을 익히는 것이 목적이 아니라, Redis Cluster 환경에서 발생하는 데이터 복제, 장애 복구, 분산 락, Hot Key 문제를 직접 실험하고 분석하여 실무 환경에서 설명 가능한 수준의 이해를 얻는 것을 목표로 한다.

---

# Project Goals

다음 항목을 직접 구축하고 검증한다.

* Redis Cluster Architecture
* Hash Slot Distribution
* Replication
* Failover
* Distributed Lock
* Lock Consistency
* Replication Lag
* Hot Key
* Network Partition
* Cluster Reconfiguration

모든 결과는 메트릭과 실험 결과를 기반으로 분석한다.

---

# Non Goals

본 프로젝트의 목적이 아닌 항목

* 비즈니스 서비스 개발
* 복잡한 도메인 모델링
* 인증/인가 구현
* 화면(UI) 개발
* MSA 설계
* Redis 외 인프라 학습

Redis 동작 원리 검증에 필요한 최소한의 코드만 작성한다.

---

# Tech Stack

## Application

* Java 17
* Spring Boot 3.x

## Redis

* Redis 7.x
* Redis Cluster
* Lettuce
* Redisson

## Observability

* Spring Actuator
* Micrometer
* Redis Exporter
* Prometheus
* Grafana

## Test

* JUnit5
* Testcontainers

## Environment

* Docker
* Docker Compose

---

# Architecture

## Redis Cluster

3 Master + 3 Replica 구조를 사용한다.

Master1 ─ Replica1

Master2 ─ Replica2

Master3 ─ Replica3

Redis Cluster는 직접 구축한다.

자동 Cluster 구성 이미지를 사용하지 않는다.

금지

* Bitnami Redis Cluster 자동 생성 이미지
* 원클릭 Cluster 생성 솔루션
* Cluster 내부 동작을 숨기는 도구

필수

* Redis Node 개별 컨테이너 생성
* redis-cli --cluster create 사용
* Cluster 상태 직접 검증

---

## Application

동일한 Spring Boot 애플리케이션을 3개 실행한다.

App1

App2

App3

멀티 인스턴스 환경에서 경쟁 상황을 재현한다.

---

## Monitoring

Redis Exporter

↓

Prometheus

↓

Grafana

Application Metrics

↓

Micrometer

↓

Prometheus

↓

Grafana

---

# Development Principles

본 프로젝트는 기능 개발 프로젝트가 아니다.

모든 구현은 실험을 위한 목적으로만 작성한다.

새로운 기능을 구현하기 전에 반드시 아래를 정의한다.

1. 무엇을 검증할 것인가
2. 어떤 결과를 예상하는가
3. 어떤 메트릭을 수집할 것인가

실험 가능한 코드만 작성한다.

모든 실험은 재현 가능해야 한다.

---

# Redis Client Strategy

## Lettuce

목적

* Cluster 연결
* Hash Slot 검증
* MOVED Redirect 검증
* ASK Redirect 검증
* Replication 검증
* Failover 검증
* SET NX PX 기반 Lock 직접 구현

## Redisson

목적

* RLock
* Watchdog
* Reentrant Lock
* Fair Lock
* Multi Lock

Lettuce 구현과 비교 분석한다.

---

# Test Strategy

## Manual Test

REST API 기반 실험

예시

POST /cluster/key-distribution

POST /lock/acquire

POST /lock/benchmark

POST /failover/start

POST /hotkey/test

---

## Automated Test

JUnit 기반 자동화 테스트

예시

Replication Test

Failover Test

Lock Consistency Test

Hot Key Test

Network Partition Test

---

# Fault Injection Strategy

## Scenario 1 - Process Failure

docker stop

검증

* Failover
* Replica Promotion
* Connection Recovery

---

## Scenario 2 - Network Partition

docker network disconnect

검증

* Cluster Reconfiguration
* Data Availability
* Lock Consistency
* Split Brain 가능성

---

# Metrics

## Redis Metrics

* Memory Usage
* CPU Usage
* Connected Clients
* Replication Lag
* Replication Offset
* Cluster State

## Application Metrics

* API Response Time
* Request Count
* Error Count

## Lock Metrics

* Lock Acquisition Success Count
* Lock Acquisition Failure Count
* Lock Wait Time
* Duplicate Execution Count

## Failover Metrics

* Failover Completion Time
* Recovery Time
* Request Failure Count During Failover

## Hot Key Metrics

* Throughput
* Latency
* Node Resource Usage

---

# Branch Strategy

main

phase1-cluster

phase2-observability

phase3-lock-with-lettuce

phase4-redisson

phase5-failover

phase6-chaos-testing

phase7-report

현재 Phase 범위를 벗어난 기능을 구현하지 않는다.

다음 Phase 기능을 미리 구현하지 않는다.

각 Phase 완료 후 Pull Request를 통해 병합한다.

---

# Phase 1 - Cluster

목표

* Redis Cluster 구축
* Hash Slot 이해
* MOVED Redirect 이해

완료 기준

* 3 Master + 3 Replica Cluster 생성
* cluster nodes 검증
* cluster slots 검증
* keyslot 검증

학습 질문

* Redis Cluster는 왜 16384 Slot을 사용하는가?
* Hash Slot은 어떻게 계산되는가?
* MOVED는 왜 발생하는가?

---

# Phase 2 - Observability

목표

* Redis 상태 측정
* Application 상태 측정

완료 기준

* Prometheus 연동
* Grafana Dashboard 구성
* Redis Exporter 연동
* Micrometer 연동

학습 질문

* Replication Lag는 어떻게 측정하는가?
* Failover Time은 어떻게 측정하는가?

---

# Phase 3 - Lock with Lettuce

목표

* SET NX PX 기반 Lock 구현
* Lua Unlock 구현

완료 기준

* 동시성 경쟁 테스트
* TTL 검증
* 중복 실행 방지 검증

학습 질문

* DEL만 호출하면 왜 위험한가?
* Lua Script가 필요한 이유는 무엇인가?

---

# Phase 4 - Redisson

목표

* Redisson Lock 이해

완료 기준

* Watchdog 검증
* Reentrant Lock 검증
* Fair Lock 검증

학습 질문

* Redisson은 내부적으로 어떻게 동작하는가?
* Watchdog가 필요한 이유는 무엇인가?

---

# Phase 5 - Failover

목표

* Replica Promotion 이해

실험

* Master 강제 종료

측정

* Failover Time
* Error Count
* Recovery Time

학습 질문

* Failover 중 요청은 어떻게 처리되는가?
* 데이터 유실 가능성은 없는가?

---

# Phase 6 - Chaos Testing

목표

* Network Partition
* Lock Loss
* Hot Key

실험

* Network Disconnect
* Process Failure

학습 질문

* Split Brain이 발생할 수 있는가?
* 락은 항상 안전한가?
* Replication은 언제 데이터 유실을 유발할 수 있는가?

---

# Phase 7 - Report

목표

실험 결과 문서화

산출물

* README
* Architecture Diagram
* Experiment Report
* Troubleshooting Notes

---

# Experiment Report Template

## Goal

무엇을 검증하는가

## Environment

* Redis Version
* Cluster Topology
* Application Count

## Scenario

실험 절차

## Metrics

수집한 메트릭

## Result

실험 결과

## Analysis

원인 분석

## Conclusion

최종 결론

---

# Deliverables

각 Phase 종료 시 반드시 아래 문서를 갱신한다.

* README.md
* Architecture Diagram
* Experiment Report
* Troubleshooting Notes

코드보다 실험 결과와 분석을 우선한다.
